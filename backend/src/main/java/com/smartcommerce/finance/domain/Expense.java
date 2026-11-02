package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A stored expense, also the store's payable (SC-020, SC-051); cancelled ones stay, with who, when
 * and why. {@code paidAccountId} is null for expenses paid before SC-051 ("conta não informada").
 */
public record Expense(
    UUID id,
    String category,
    String kind,
    String description,
    BigDecimal amount,
    LocalDate competenceDate,
    LocalDate dueDate,
    String supplier,
    String documentNumber,
    Integer installmentNumber,
    Integer installmentCount,
    String recurrence,
    LocalDate paidOn,
    UUID paidAccountId,
    String paidAccountName,
    BigDecimal surcharge,
    BigDecimal discount,
    String status,
    Instant createdAt,
    Instant cancelledAt,
    String cancelReason) {
  public enum Situation {
    OPEN,
    OVERDUE,
    PAID,
    CANCELLED
  }

  public boolean active() {
    return "ACTIVE".equals(status);
  }

  /** Derived, never stored: cancelled, paid, past due (from the day after the due date) or open. */
  public Situation situation(LocalDate today) {
    if (!active()) return Situation.CANCELLED;
    if (paidOn != null) return Situation.PAID;
    return dueDate.isBefore(today) ? Situation.OVERDUE : Situation.OPEN;
  }

  /** What left the account: the amount plus interest or fine, minus the discount. */
  public BigDecimal paidTotal() {
    return amount.add(surcharge).subtract(discount);
  }

  public boolean monthly() {
    return "MONTHLY".equals(recurrence);
  }
}
