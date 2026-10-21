package com.smartcommerce.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment facts shown to the store OWNER (SC-015). The Orders API reports no fee or net amount, so
 * none is stored or shown as if known (to be decided with the Sprint 5 finance work). {@code
 * refund} is null when no refund was asked for (SC-016).
 */
public record PaymentSummary(
    String providerOrderId,
    String status,
    String providerStatus,
    String providerStatusDetail,
    BigDecimal amount,
    BigDecimal paidAmount,
    Instant approvedAt,
    Instant checkedAt,
    String lastCheckError,
    Refund refund) {
  /** The store's full refund: REQUESTED until the provider confirms it, then CONFIRMED. */
  public record Refund(
      String status,
      String reason,
      boolean restock,
      Instant requestedAt,
      Instant providerAcceptedAt,
      Instant confirmedAt,
      String lastError) {}
}
