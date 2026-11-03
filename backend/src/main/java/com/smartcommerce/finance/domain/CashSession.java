package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A cash session of one drawer (SC-052, ADR-0019 C5). While open, {@code countedAmount}, {@code
 * expectedAmount} and the closing fields are null. Expected is the drawer balance right before the
 * closing difference; only the OWNER ever sees it.
 */
public record CashSession(
    UUID id,
    UUID accountId,
    String accountName,
    String status,
    BigDecimal openingAmount,
    String openedBy,
    Instant openedAt,
    UUID reopenedFrom,
    String reopenReason,
    BigDecimal countedAmount,
    BigDecimal expectedAmount,
    String closedBy,
    Instant closedAt,
    UUID closeRequestId) {
  public boolean open() {
    return "OPEN".equals(status);
  }

  /** Counted minus expected: positive is cash left over, negative is cash missing. */
  public BigDecimal difference() {
    return countedAmount == null ? null : countedAmount.subtract(expectedAmount);
  }

  /** One movement recorded in a session, with who recorded it. */
  public record Movement(
      UUID id,
      String kind,
      BigDecimal amount,
      String description,
      String recordedBy,
      Instant createdAt) {}
}
