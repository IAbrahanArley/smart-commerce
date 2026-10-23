package com.smartcommerce.advisor.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Store alerts (SC-024, ADR-0016 A6): deterministic rules over figures the backend already
 * computes. Each alert carries a stable {@code code}, a {@code severity} (CRITICAL, ATTENTION,
 * INFO), its supporting figure ({@code count} and, when money, {@code amount}) and the admin screen
 * that resolves it ({@code target}: PRODUCTS, ORDERS, PAYMENTS, FINANCE). Texts belong to the
 * admin; no buyer data is ever included.
 */
public record AlertsView(Instant generatedAt, String marginPeriod, List<Alert> alerts) {
  public record Alert(String code, String severity, Long count, String amount, String target) {}
}
