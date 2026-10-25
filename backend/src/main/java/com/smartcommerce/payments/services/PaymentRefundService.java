package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentRecord;
import com.smartcommerce.payments.domain.SellerCredentials;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentRefundRepository;
import com.smartcommerce.payments.repositories.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full refund asked by the store OWNER (SC-016, ADR-0013 P9). The caller (orders) has already
 * checked the OWNER and that the order is PAID; the tenant is trusted.
 *
 * <ol>
 *   <li>Under the payment row lock, the refund is recorded once per payment with its reason, the
 *       stock choice and who asked. A repeat with the same choice (double click, retry) reuses it;
 *       another choice is a conflict.
 *   <li>Outside any transaction the provider is asked, always with the same idempotency key per
 *       order, so it never refunds twice. A provider failure is recorded; the store may retry.
 *   <li>The order is never marked refunded from that answer: the provider payment is read back
 *       (now, and later by the reconciliation or a webhook) and only a refunded payment settles it,
 *       once.
 * </ol>
 */
@Service
public class PaymentRefundService {
  private static final Logger LOG = LoggerFactory.getLogger(PaymentRefundService.class);
  private final PaymentRepository payments;
  private final PaymentRefundRepository refunds;
  private final PaymentConnectionService connections;
  private final PaymentConfirmationService confirmations;
  private final PaymentProviders providers;
  private final Clock clock;
  private final TransactionTemplate writes;

  public PaymentRefundService(
      PaymentRepository payments,
      PaymentRefundRepository refunds,
      PaymentConnectionService connections,
      PaymentConfirmationService confirmations,
      PaymentProviders providers,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.payments = payments;
    this.refunds = refunds;
    this.connections = connections;
    this.confirmations = confirmations;
    this.providers = providers;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public void request(UUID tenant, UUID order, UUID actor, String reason, boolean restock) {
    var client = providers.requireCheckout();
    PaymentRecord payment =
        writes.execute(
            tx -> {
              PaymentRecord locked =
                  payments
                      .lockByOrder(tenant, order)
                      .filter(found -> found.approved() || found.refunded())
                      .orElseThrow(
                          () -> new PaymentException(PaymentException.Reason.NOT_REFUNDABLE));
              if (locked.refunded()) return locked;
              Instant now = now();
              refunds.request(
                  tenant, locked.id(), order, locked.amount(), reason, restock, actor, now);
              var stored = refunds.find(tenant, locked.id()).orElseThrow();
              if (stored.restock() != restock)
                throw new PaymentException(PaymentException.Reason.REFUND_CONFLICT);
              // The reconciliation keeps reading the order until the refund is confirmed.
              payments.checkFrom(tenant, locked.id(), now.plus(PaymentRepository.FIRST_CHECK));
              return locked;
            });
    if (payment.refunded()) return;

    SellerCredentials seller = connections.sellerCredentials(tenant);
    if (!seller.providerUserId().equals(payment.providerUserId()))
      throw new PaymentException(PaymentException.Reason.ACCOUNT_CHANGED);
    try {
      client.refundPayment(
          seller.accessToken(), payment.providerPaymentId(), idempotencyKey(order));
    } catch (PaymentException failure) {
      LOG.warn("Refund request failed for order {}", order);
      writes.executeWithoutResult(tx -> refunds.failed(tenant, payment.id(), "PROVIDER_ERROR"));
      // A retry of a refund already done is refused by the provider: the payment read back says.
      if (settledAnyway(tenant, payment.providerOrderId())) return;
      throw failure;
    }
    writes.executeWithoutResult(tx -> refunds.accepted(tenant, payment.id(), now()));
    try {
      confirmations.confirm(tenant, payment.providerOrderId());
    } catch (PaymentException pending) {
      // Not confirmed yet: the reconciliation (or a webhook) settles it later.
    }
  }

  private boolean settledAnyway(UUID tenant, String preference) {
    try {
      var outcome = confirmations.confirm(tenant, preference);
      return outcome == PaymentConfirmationService.Outcome.REFUNDED
          || outcome == PaymentConfirmationService.Outcome.ALREADY_REFUNDED;
    } catch (PaymentException unreadable) {
      return false;
    }
  }

  /** One full refund per order: the same key on every attempt (ADR-0013 P9). */
  static String idempotencyKey(UUID order) {
    return UUID.nameUUIDFromBytes(("sc-refund:" + order).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
