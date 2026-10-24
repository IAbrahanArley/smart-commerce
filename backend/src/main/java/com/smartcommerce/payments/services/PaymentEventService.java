package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.PaymentEventRecord;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.repositories.PaymentEventRepository;
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
 * Processes stored notifications (SC-014). Claims a batch in its own short transaction, then
 * confirms each event's provider payment outside it. Redelivered, out-of-order or concurrent events
 * are harmless: confirmation is idempotent and always reads the provider's current state. Transient
 * failures retry with backoff; after {@link #MAX_ATTEMPTS} the event is FAILED and the
 * reconciliation routine (SC-015) remains the safety net.
 */
@Service
public class PaymentEventService {
  static final int MAX_ATTEMPTS = 8;

  /** A claimed event comes back after this long if its worker dies mid-way. */
  static final Duration LEASE = Duration.ofMinutes(5);

  private static final Logger LOG = LoggerFactory.getLogger(PaymentEventService.class);
  private final PaymentEventRepository events;
  private final PaymentConfirmationService confirmations;
  private final Clock clock;
  private final TransactionTemplate writes;

  public PaymentEventService(
      PaymentEventRepository events,
      PaymentConfirmationService confirmations,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.events = events;
    this.confirmations = confirmations;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** Processes up to {@code limit} due events; returns how many were claimed. */
  public int processDue(int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid batch size");
    Instant now = now();
    List<PaymentEventRecord> due =
        writes.execute(tx -> events.claimDue(now, now.plus(LEASE), limit));
    for (PaymentEventRecord event : due) process(event);
    return due.size();
  }

  private void process(PaymentEventRecord event) {
    if (event.tenant() == null) {
      finish(event, "IGNORED", "UNKNOWN_ORDER");
      return;
    }
    try {
      // The payment topic names a provider payment; anything older names a preference.
      var outcome =
          "payment".equals(event.topic())
              ? confirmations.confirmProviderPayment(event.tenant(), event.providerOrderId())
              : confirmations.confirm(event.tenant(), event.providerOrderId());
      switch (outcome) {
        case UNKNOWN -> finish(event, "IGNORED", "UNKNOWN_ORDER");
        case NOT_PAID -> finish(event, "PROCESSED", "NOT_PAID");
        default -> finish(event, "PROCESSED", null);
      }
    } catch (PaymentException failure) {
      if (failure.reason() == PaymentException.Reason.ACCOUNT_CHANGED)
        finish(event, "FAILED", failure.reason().name());
      else retryOrFail(event, failure.reason().name());
    } catch (RuntimeException failure) {
      LOG.warn("Payment event {} failed: {}", event.id(), failure.getClass().getSimpleName());
      retryOrFail(event, "INTERNAL");
    }
  }

  private void retryOrFail(PaymentEventRecord event, String error) {
    if (event.attempts() >= MAX_ATTEMPTS) {
      finish(event, "FAILED", error);
      return;
    }
    Instant next = now().plus(backoff(event.attempts()));
    writes.executeWithoutResult(tx -> events.retry(event.id(), next, error));
  }

  /** 1, 2, 4, 8 … minutes after each failed attempt, capped at 6 hours. */
  static Duration backoff(int attempts) {
    long minutes = 1L << Math.min(Math.max(attempts - 1, 0), 9);
    return Duration.ofMinutes(Math.min(minutes, 360));
  }

  private void finish(PaymentEventRecord event, String status, String error) {
    writes.executeWithoutResult(tx -> events.finish(event.id(), status, error, now()));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
