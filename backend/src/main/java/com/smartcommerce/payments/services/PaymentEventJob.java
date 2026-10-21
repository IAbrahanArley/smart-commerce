package com.smartcommerce.payments.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the notification queue (SC-014) in the backend process. Safe with several instances (SKIP
 * LOCKED). Failures are logged by type only, without buyer data or tokens.
 */
@Component
@ConditionalOnProperty(name = "payments.events.enabled", matchIfMissing = true)
public class PaymentEventJob {
  static final int BATCH = 20;
  private static final int MAX_ROUNDS = 10;
  private static final Logger LOG = LoggerFactory.getLogger(PaymentEventJob.class);
  private final PaymentEventService events;

  public PaymentEventJob(PaymentEventService events) {
    this.events = events;
  }

  @Scheduled(
      fixedDelayString = "${payments.events.interval:PT10S}",
      initialDelayString = "${payments.events.interval:PT10S}")
  public void run() {
    try {
      int rounds = 0;
      while (events.processDue(BATCH) == BATCH && ++rounds < MAX_ROUNDS) {
        // A full batch means more may be due: keep draining, bounded per run.
      }
    } catch (RuntimeException failure) {
      LOG.warn("Payment event batch failed: {}", failure.getClass().getSimpleName());
    }
  }
}
