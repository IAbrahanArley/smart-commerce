package com.smartcommerce.finance.dto.response;

import com.smartcommerce.finance.domain.Expense;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An expense or payable as the store OWNER sees it (SC-020, SC-051); money keeps the two-decimal
 * text contract. {@code situation} is derived on the day of the request; {@code paidTotal} is what
 * left the account (null while unpaid). A paid one with no {@code paidAccountId} was paid before
 * SC-051 ("conta não informada").
 */
public record ExpenseView(
    UUID id,
    String category,
    String kind,
    String description,
    String amount,
    LocalDate competenceDate,
    LocalDate dueDate,
    String supplier,
    String documentNumber,
    Integer installmentNumber,
    Integer installmentCount,
    String recurrence,
    String situation,
    LocalDate paidOn,
    UUID paidAccountId,
    String paidAccountName,
    String surcharge,
    String discount,
    String paidTotal,
    String status,
    Instant createdAt,
    Instant cancelledAt,
    String cancelReason) {
  public static ExpenseView of(Expense expense, LocalDate today) {
    return new ExpenseView(
        expense.id(),
        expense.category(),
        expense.kind(),
        expense.description(),
        money(expense.amount()),
        expense.competenceDate(),
        expense.dueDate(),
        expense.supplier(),
        expense.documentNumber(),
        expense.installmentNumber(),
        expense.installmentCount(),
        expense.recurrence(),
        expense.situation(today).name(),
        expense.paidOn(),
        expense.paidAccountId(),
        expense.paidAccountName(),
        money(expense.surcharge()),
        money(expense.discount()),
        expense.paidOn() == null ? null : money(expense.paidTotal()),
        expense.status(),
        expense.createdAt(),
        expense.cancelledAt(),
        expense.cancelReason());
  }

  static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
