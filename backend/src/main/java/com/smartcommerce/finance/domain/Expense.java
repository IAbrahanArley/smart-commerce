package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A stored manual expense (SC-020); cancelled ones stay, with who, when and why. */
public record Expense(
    UUID id,
    String category,
    String kind,
    String description,
    BigDecimal amount,
    LocalDate competenceDate,
    LocalDate paidOn,
    String status,
    Instant createdAt,
    Instant cancelledAt,
    String cancelReason) {
  public boolean active() {
    return "ACTIVE".equals(status);
  }
}
