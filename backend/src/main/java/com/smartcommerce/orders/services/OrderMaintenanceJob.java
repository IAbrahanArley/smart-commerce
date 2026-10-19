package com.smartcommerce.orders.services;

import java.time.Duration;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules the order lifecycle batches in the backend process (SC-010): expiration every minute
 * and D2 anonymization once a day. Safe with several instances (SKIP LOCKED and compare-and-set).
 * Each run drains its queue in batches until a batch comes back short or a time budget ends, so a
 * backlog never outgrows the daily anonymization promised by the privacy notice (SC-011 review).
 * Failures are logged without order or buyer data and retried on the next run.
 */
@Component
@ConditionalOnProperty(name = "orders.maintenance.enabled", matchIfMissing = true)
public class OrderMaintenanceJob {
  static final int BATCH = 100;

  /** Expiration runs every minute; anonymization once a day, so it may work longer per run. */
  static final Duration EXPIRATION_BUDGET = Duration.ofSeconds(30);

  static final Duration ANONYMIZATION_BUDGET = Duration.ofMinutes(10);

  private static final Logger LOG = LoggerFactory.getLogger(OrderMaintenanceJob.class);
  private final OrderLifecycleService lifecycle;

  public OrderMaintenanceJob(OrderLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Scheduled(
      fixedDelayString = "${orders.maintenance.expiration-interval:PT1M}",
      initialDelayString = "${orders.maintenance.expiration-interval:PT1M}")
  public void expire() {
    run("expiration", () -> lifecycle.expireDue(BATCH), EXPIRATION_BUDGET);
  }

  @Scheduled(
      fixedDelayString = "${orders.maintenance.anonymization-interval:PT24H}",
      initialDelayString = "${orders.maintenance.anonymization-initial-delay:PT10M}")
  public void anonymize() {
    run("anonymization", () -> lifecycle.anonymizeDue(BATCH), ANONYMIZATION_BUDGET);
  }

  private static void run(String name, IntSupplier batch, Duration budget) {
    try {
      drain(batch, budget, System::nanoTime);
    } catch (RuntimeException failure) {
      LOG.warn("Order {} batch failed: {}", name, failure.getClass().getSimpleName());
    }
  }

  /** Runs batches while they come back full and the budget lasts; returns how many ran. */
  static int drain(IntSupplier batch, Duration budget, LongSupplier nanos) {
    long deadline = nanos.getAsLong() + budget.toNanos();
    int runs = 0;
    do runs++;
    while (batch.getAsInt() == BATCH && nanos.getAsLong() - deadline < 0);
    return runs;
  }
}
