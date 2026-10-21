package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.repositories.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Minimal reconciliation (SC-015, ADR-0013 P5, P6): queries the provider for every provider order
 * still waiting for confirmation, using the same {@link PaymentConfirmationService} path as the
 * webhook, so it is idempotent and races with notifications or the expiration batch are harmless.
 * It is the main confirmation path where no webhook reaches the backend (local development, P6).
 *
 * <ul>
 *   <li>Every minute while the provider order is open; every {@link #LATE_INTERVAL} for {@link
 *       #HORIZON} after it expires, to catch late payments (P4); then no more.
 *   <li>Each payment is queried with the token of its own tenant.
 *   <li>A failure (provider down, account changed, mismatching answer) is recorded on the payment
 *       for the store to see and retried on the same schedule.
 * </ul>
 */
@Service
public class PaymentReconciliationService {
  static final Duration OPEN_INTERVAL = Duration.ofMinutes(1);
  static final Duration LATE_INTERVAL = Duration.ofMinutes(15);
  static final Duration HORIZON = Duration.ofHours(24);

  /**
   * SC-017 review: a payment that could not be read because the store lost or changed its account,
   * or a refund still waiting, is looked at for this long instead, so money taken by the provider
   * is never silently left unconfirmed; a reconnection queues it again at once.
   */
  static final Duration EXTENDED_HORIZON = Duration.ofDays(30);

  static final Duration LEASE = Duration.ofMinutes(5);
  private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciliationService.class);
  private final PaymentRepository payments;
  private final PaymentConfirmationService confirmations;
  private final Clock clock;
  private final TransactionTemplate writes;

  public PaymentReconciliationService(
      PaymentRepository payments,
      PaymentConfirmationService confirmations,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.payments = payments;
    this.confirmations = confirmations;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** Reconciles up to {@code limit} due payments; returns how many were claimed. */
  public int reconcileDue(int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid batch size");
    Instant now = now();
    List<PaymentRepository.DueCheck> due =
        writes.execute(
            tx ->
                payments.claimDueChecks(
                    now, now.minus(HORIZON), now.minus(EXTENDED_HORIZON), now.plus(LEASE), limit));
    for (var check : due) reconcile(check);
    return due.size();
  }

  private void reconcile(PaymentRepository.DueCheck check) {
    String error = null;
    try {
      confirmations.confirm(check.tenant(), check.providerOrderId());
    } catch (PaymentException failure) {
      error = failure.reason().name();
    } catch (RuntimeException failure) {
      LOG.warn("Payment reconciliation failed: {}", failure.getClass().getSimpleName());
      error = "INTERNAL";
    }
    Instant now = now();
    // A refund waiting for confirmation is looked at every minute (SC-016).
    Instant next =
        now.plus(
            check.refund() || now.isBefore(check.providerExpiresAt())
                ? OPEN_INTERVAL
                : LATE_INTERVAL);
    String recorded = error;
    // A confirmed payment is no longer CREATED, so this changes nothing for it.
    writes.executeWithoutResult(
        tx -> payments.scheduleCheck(check.tenant(), check.id(), next, recorded));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
