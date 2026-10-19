package com.smartcommerce.orders.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class OrderMaintenanceJobTest {
  @Test
  void drainsWhileBatchesAreFullAndStopsOnAShortBatchOrTheBudget() {
    var clock = new AtomicLong();
    // A backlog of 250 rows: two full batches and a short one, well inside the budget.
    int[] left = {250};
    int runs =
        OrderMaintenanceJob.drain(
            () -> {
              int done = Math.min(OrderMaintenanceJob.BATCH, left[0]);
              left[0] -= done;
              return done;
            },
            Duration.ofMinutes(10),
            clock::get);
    assertThat(runs).isEqualTo(3);
    assertThat(left[0]).isZero();
    // An endless backlog stops when the budget is spent (each batch "takes" one second).
    int bounded =
        OrderMaintenanceJob.drain(
            () -> {
              clock.addAndGet(Duration.ofSeconds(1).toNanos());
              return OrderMaintenanceJob.BATCH;
            },
            Duration.ofSeconds(30),
            clock::get);
    assertThat(bounded).isBetween(30, 31);
  }
}
