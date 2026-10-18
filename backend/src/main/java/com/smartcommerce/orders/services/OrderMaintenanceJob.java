package com.smartcommerce.orders.services;

import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the order lifecycle batches in the backend process (SC-010): expiration every minute
 * and D2 anonymization once a day. Safe with several instances (SKIP LOCKED and compare-and-set).
 * Failures are logged without order or buyer data and retried on the next run.
 */
@Component
@ConditionalOnProperty(name = "orders.maintenance.enabled", matchIfMissing = true)
public class OrderMaintenanceJob {
  static final int BATCH = 100;

  /** Caps one run; a backlog continues on the next run instead of holding the scheduler. */
  static final int MAX_BATCHES = 20;

  private static final Logger LOG = LoggerFactory.getLogger(OrderMaintenanceJob.class);
  private final OrderLifecycleService lifecycle;

  public OrderMaintenanceJob(OrderLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Scheduled(
      fixedDelayString = "${orders.maintenance.expiration-interval:PT1M}",
      initialDelayString = "${orders.maintenance.expiration-interval:PT1M}")
  public void expire() {
    run("expiration", () -> lifecycle.expireDue(BATCH));
  }

  @Scheduled(
      fixedDelayString = "${orders.maintenance.anonymization-interval:PT24H}",
      initialDelayString = "${orders.maintenance.anonymization-initial-delay:PT10M}")
  public void anonymize() {
    run("anonymization", () -> lifecycle.anonymizeDue(BATCH));
  }

  private static void run(String name, IntSupplier batch) {
    try {
      // A full batch means more rows may be due; keep going up to the cap.
      for (int i = 0; i < MAX_BATCHES; i++) if (batch.getAsInt() < BATCH) return;
    } catch (RuntimeException failure) {
      LOG.warn("Order {} batch failed: {}", name, failure.getClass().getSimpleName());
    }
  }
}
