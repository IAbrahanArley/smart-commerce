package com.smartcommerce.finance.dto.response;

import com.smartcommerce.finance.domain.Expense;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Expenses of one competence month (SC-020): active totals split into fixed and variable, and every
 * expense of the month, cancelled ones included for the audit trail.
 */
public record ExpenseMonthView(
    String month, String total, String fixed, String variable, List<ExpenseView> expenses) {
  public static ExpenseMonthView of(YearMonth month, List<Expense> expenses) {
    BigDecimal fixed = sum(expenses, "FIXED");
    BigDecimal variable = sum(expenses, "VARIABLE");
    return new ExpenseMonthView(
        month.toString(),
        money(fixed.add(variable)),
        money(fixed),
        money(variable),
        expenses.stream().map(ExpenseView::of).toList());
  }

  private static BigDecimal sum(List<Expense> expenses, String kind) {
    return expenses.stream()
        .filter(expense -> expense.active() && kind.equals(expense.kind()))
        .map(Expense::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
