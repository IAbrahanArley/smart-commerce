package com.smartcommerce.finance.dto.response;

import com.smartcommerce.finance.domain.Expense;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An expense as the store OWNER sees it (SC-020); money keeps the two-decimal text contract. */
public record ExpenseView(
    UUID id,
    String category,
    String kind,
    String description,
    String amount,
    LocalDate competenceDate,
    LocalDate paidOn,
    String status,
    Instant createdAt,
    Instant cancelledAt,
    String cancelReason) {
  public static ExpenseView of(Expense expense) {
    return new ExpenseView(
        expense.id(),
        expense.category(),
        expense.kind(),
        expense.description(),
        expense.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        expense.competenceDate(),
        expense.paidOn(),
        expense.status(),
        expense.createdAt(),
        expense.cancelledAt(),
        expense.cancelReason());
  }
}
