package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentRecord;
import com.smartcommerce.payments.domain.ProviderOrderStatus;
import com.smartcommerce.payments.domain.SellerCredentials;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirms a payment by asking the provider (SC-014, ADR-0013 P5). Never trusts a notification or
 * the browser: the provider order is read with the token of the tenant that created it, checked
 * against what we stored (reference, seller account, amount), and only a paid-in-full order moves
 * the payment to APPROVED. That transition happens once, under the payment row lock, together with
 * the order transition and the stock deduction done by {@link PaymentApprovalListener}.
 */
@Service
public class PaymentConfirmationService {
  public enum Outcome {
    /** This call confirmed the payment. */
    APPROVED,
    /** Already confirmed before: nothing changed. */
    ALREADY_APPROVED,
    /** The provider does not report a full payment yet: recorded, nothing changed. */
    NOT_PAID,
    /** No payment of ours has this provider order in this tenant. */
    UNKNOWN
  }

  private final PaymentRepository payments;
  private final PaymentConnectionService connections;
  private final PaymentProviders providers;
  private final PaymentApprovalListener listener;
  private final Clock clock;
  private final TransactionTemplate writes;
  private final TransactionTemplate reads;

  public PaymentConfirmationService(
      PaymentRepository payments,
      PaymentConnectionService connections,
      PaymentProviders providers,
      PaymentApprovalListener listener,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.payments = payments;
    this.connections = connections;
    this.providers = providers;
    this.listener = listener;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.reads = new TransactionTemplate(transactions);
    this.reads.setReadOnly(true);
  }

  public Outcome confirm(UUID tenant, String providerOrderId) {
    var client = providers.requireOrders();
    Optional<PaymentRecord> stored =
        reads.execute(tx -> payments.findByProviderOrder(tenant, providerOrderId));
    if (stored.isEmpty()) return Outcome.UNKNOWN;
    PaymentRecord payment = stored.get();
    if (payment.approved()) return Outcome.ALREADY_APPROVED;

    SellerCredentials seller = connections.sellerCredentials(tenant);
    // Reconnected to another seller: that token must never read this order (A/B by account).
    if (!seller.providerUserId().equals(payment.providerUserId()))
      throw new PaymentException(PaymentException.Reason.ACCOUNT_CHANGED);
    ProviderOrderStatus order = client.fetchOrder(seller.accessToken(), providerOrderId);
    // Reference and total are mandatory; seller and currency are checked when reported.
    if (!payment.orderId().toString().equals(order.externalReference())
        || order.totalAmount() == null
        || order.totalAmount().compareTo(payment.amount()) != 0
        || (order.userId() != null && !order.userId().equals(payment.providerUserId()))
        || (order.currency() != null && !"BRL".equals(order.currency())))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);

    return writes.execute(
        tx -> {
          PaymentRecord locked =
              payments.lockByProviderOrder(tenant, providerOrderId).orElseThrow();
          if (locked.approved()) return Outcome.ALREADY_APPROVED;
          Instant now = now();
          if (!order.paidInFull(locked.amount())) {
            payments.recordCheck(
                tenant,
                locked.id(),
                order.status(),
                order.statusDetail(),
                order.totalPaidAmount(),
                now);
            return Outcome.NOT_PAID;
          }
          if (!payments.approve(
              tenant, locked.id(), order.status(), order.statusDetail(), locked.amount(), now))
            throw new IllegalStateException("Payment changed while locked");
          listener.approved(tenant, locked.orderId(), now);
          return Outcome.APPROVED;
        });
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
