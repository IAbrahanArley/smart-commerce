package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentRecord;
import com.smartcommerce.payments.domain.ProviderPayment;
import com.smartcommerce.payments.domain.SellerCredentials;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentRefundRepository;
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
 * Confirms a payment by asking the provider (SC-014, ADR-0013 P5 and the P1 change). Never trusts a
 * notification or the browser: the buyer's payment is read with the token of the tenant that
 * created the preference, checked against what we stored (reference, seller account, currency,
 * amount), and only an approved payment of the full amount moves the payment to APPROVED. That
 * transition happens once, under the payment row lock, together with the order transition and the
 * stock deduction done by {@link PaymentListener}. The same query confirms a refund the store asked
 * for (SC-016): only the approved payment reported refunded moves an APPROVED payment with a
 * pending refund to REFUNDED, once, with the order and the optional restock.
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
    /** No payment of ours has this preference (or the notified payment) in this tenant. */
    UNKNOWN,
    /** This call confirmed the store's refund. */
    REFUNDED,
    /** Already refunded before: nothing changed. */
    ALREADY_REFUNDED,
    /** The refund was asked for but the provider does not report it done yet. */
    REFUND_PENDING
  }

  private final PaymentRepository payments;
  private final PaymentRefundRepository refunds;
  private final PaymentConnectionService connections;
  private final PaymentProviders providers;
  private final PaymentListener listener;
  private final Clock clock;
  private final TransactionTemplate writes;
  private final TransactionTemplate reads;

  public PaymentConfirmationService(
      PaymentRepository payments,
      PaymentRefundRepository refunds,
      PaymentConnectionService connections,
      PaymentProviders providers,
      PaymentListener listener,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.payments = payments;
    this.refunds = refunds;
    this.connections = connections;
    this.providers = providers;
    this.listener = listener;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.reads = new TransactionTemplate(transactions);
    this.reads.setReadOnly(true);
  }

  /**
   * Confirms the payment of the preference {@code providerOrderId} (reconciliation, refund): the
   * buyer's payment is searched by our order reference with the token of the tenant that created
   * the preference.
   */
  public Outcome confirm(UUID tenant, String providerOrderId) {
    var client = providers.requireCheckout();
    Optional<PaymentRecord> stored =
        reads.execute(tx -> payments.findByProviderOrder(tenant, providerOrderId));
    if (stored.isEmpty()) return Outcome.UNKNOWN;
    PaymentRecord payment = stored.get();
    Outcome settled = alreadySettled(tenant, payment);
    if (settled != null) return settled;
    SellerCredentials seller = seller(tenant, payment);
    Optional<ProviderPayment> found =
        client.findPayment(seller.accessToken(), payment.orderId().toString());
    if (found.isEmpty()) return payment.approved() ? Outcome.REFUND_PENDING : Outcome.NOT_PAID;
    return apply(tenant, payment, found.get());
  }

  /**
   * Confirms from a notified provider payment (webhook, topic {@code payment}). The payment is read
   * with the tenant's token and must reference one of the tenant's orders; anything else is
   * UNKNOWN.
   */
  public Outcome confirmProviderPayment(UUID tenant, String providerPaymentId) {
    var client = providers.requireCheckout();
    // A payment we already settled under this id needs no provider read.
    Optional<PaymentRecord> known =
        reads.execute(tx -> payments.findByProviderPayment(tenant, providerPaymentId));
    if (known.isPresent()) {
      Outcome settled = alreadySettled(tenant, known.get());
      if (settled != null) return settled;
    }
    SellerCredentials current = connections.sellerCredentials(tenant);
    ProviderPayment notified = client.fetchPayment(current.accessToken(), providerPaymentId);
    UUID order = orderReference(notified.externalReference());
    if (order == null) return Outcome.UNKNOWN;
    Optional<PaymentRecord> stored = reads.execute(tx -> payments.find(tenant, order));
    if (stored.isEmpty() || !stored.get().created()) return Outcome.UNKNOWN;
    PaymentRecord payment = stored.get();
    Outcome settled = alreadySettled(tenant, payment);
    if (settled != null) return settled;
    seller(tenant, payment);
    return apply(tenant, payment, notified);
  }

  /** REFUNDED, or APPROVED without a refund to confirm: nothing to ask the provider. */
  private Outcome alreadySettled(UUID tenant, PaymentRecord payment) {
    if (payment.refunded()) return Outcome.ALREADY_REFUNDED;
    boolean refundPending =
        payment.approved()
            && Boolean.TRUE.equals(
                reads.execute(
                    tx ->
                        refunds
                            .find(tenant, payment.id())
                            .map(PaymentRefundRepository.Refund::pending)
                            .orElse(false)));
    return payment.approved() && !refundPending ? Outcome.ALREADY_APPROVED : null;
  }

  private SellerCredentials seller(UUID tenant, PaymentRecord payment) {
    SellerCredentials seller = connections.sellerCredentials(tenant);
    // Reconnected to another seller: that token must never read this order (A/B by account).
    if (!seller.providerUserId().equals(payment.providerUserId()))
      throw new PaymentException(PaymentException.Reason.ACCOUNT_CHANGED);
    return seller;
  }

  private Outcome apply(UUID tenant, PaymentRecord payment, ProviderPayment provider) {
    // Reference is mandatory; seller and currency are checked when reported. The buyer pays the
    // preference's exact amount, so an approval of any other amount is a divergence, never a
    // partial payment. Once approved, only that same provider payment can refund it.
    if (!payment.orderId().toString().equals(provider.externalReference())
        || ("approved".equals(provider.status()) && !provider.paidInFull(payment.amount()))
        || (provider.collectorId() != null
            && !provider.collectorId().equals(payment.providerUserId()))
        || (provider.currency() != null && !"BRL".equals(provider.currency()))
        || (payment.providerPaymentId() != null
            && !payment.providerPaymentId().equals(provider.id())))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);

    return writes.execute(
        tx -> {
          PaymentRecord locked = payments.lockByOrder(tenant, payment.orderId()).orElseThrow();
          if (locked.refunded()) return Outcome.ALREADY_REFUNDED;
          Instant now = now();
          if (locked.approved()) return settleRefund(tenant, locked, provider, now);
          if (!provider.paidInFull(locked.amount())) {
            payments.recordCheck(
                tenant, locked.id(), provider.status(), provider.statusDetail(), null, now);
            return Outcome.NOT_PAID;
          }
          if (!payments.approve(
              tenant,
              locked.id(),
              provider.id(),
              provider.status(),
              provider.statusDetail(),
              locked.amount(),
              now)) throw new IllegalStateException("Payment changed while locked");
          listener.approved(tenant, locked.orderId(), now);
          return Outcome.APPROVED;
        });
  }

  private Outcome settleRefund(
      UUID tenant, PaymentRecord locked, ProviderPayment provider, Instant now) {
    var refund = refunds.find(tenant, locked.id()).filter(PaymentRefundRepository.Refund::pending);
    if (refund.isEmpty()) return Outcome.ALREADY_APPROVED;
    if (!provider.id().equals(locked.providerPaymentId()) || !provider.refunded())
      return Outcome.REFUND_PENDING;
    if (!payments.markRefunded(tenant, locked.id(), provider.status(), provider.statusDetail(), now)
        || !refunds.confirm(tenant, locked.id(), now))
      throw new IllegalStateException("Refund changed while locked");
    listener.refunded(tenant, locked.orderId(), refund.get().restock(), now);
    return Outcome.REFUNDED;
  }

  private static UUID orderReference(String value) {
    if (value == null
        || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      return null;
    return UUID.fromString(value);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
