package com.smartcommerce.payments.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the reconciliation (SC-015) in the backend process every minute. Safe with several instances
 * (SKIP LOCKED). Failures are logged by type only.
 */
@Component
@ConditionalOnProperty(name = "payments.reconciliation.enabled", matchIfMissing = true)
public class PaymentReconciliationJob {
  static final int BATCH = 20;
  private static final int MAX_ROUNDS = 10;
  private static final Logger LOG = LoggerFactory.getLogger(PaymentReconciliationJob.class);
  private final PaymentReconciliationService reconciliation;

  public PaymentReconciliationJob(PaymentReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Scheduled(
      fixedDelayString = "${payments.reconciliation.interval:PT1M}",
      initialDelayString = "${payments.reconciliation.interval:PT1M}")
  public void run() {
    try {
      int rounds = 0;
      while (reconciliation.reconcileDue(BATCH) == BATCH && ++rounds < MAX_ROUNDS) {
        // A full batch means more may be due: keep draining, bounded per run.
      }
    } catch (RuntimeException failure) {
      LOG.warn("Payment reconciliation batch failed: {}", failure.getClass().getSimpleName());
    }
  }
}
