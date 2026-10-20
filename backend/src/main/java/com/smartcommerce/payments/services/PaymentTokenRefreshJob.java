package com.smartcommerce.payments.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes Mercado Pago tokens close to expiry once a day (SC-012), so a store that has not sold
 * for months keeps its connection. Each account is refreshed under its row lock; several instances
 * are safe. Only the failure type is logged, never a tenant secret.
 */
@Component
@ConditionalOnProperty(name = "payments.token-refresh.enabled", matchIfMissing = true)
public class PaymentTokenRefreshJob {
  static final int BATCH = 50;
  private static final Logger LOG = LoggerFactory.getLogger(PaymentTokenRefreshJob.class);
  private final PaymentConnectionService connections;

  public PaymentTokenRefreshJob(PaymentConnectionService connections) {
    this.connections = connections;
  }

  @Scheduled(
      fixedDelayString = "${payments.token-refresh.interval:PT24H}",
      initialDelayString = "${payments.token-refresh.initial-delay:PT15M}")
  public void refresh() {
    try {
      connections.refreshDue(BATCH);
    } catch (RuntimeException failure) {
      LOG.warn("Payment token refresh failed: {}", failure.getClass().getSimpleName());
    }
  }
}
