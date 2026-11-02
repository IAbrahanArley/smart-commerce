package com.smartcommerce.finance.dto.response;

import com.smartcommerce.finance.domain.Expense;
import com.smartcommerce.finance.services.ExpenseService;
import java.util.List;

/**
 * Payables of one due month (SC-051): what is still to pay and what was paid among the bills due in
 * the month, the store's past-due bills of any month, and the bills shown (cancelled ones included
 * for the audit trail).
 */
public record ExpenseMonthView(
    String month,
    String situation,
    String toPay,
    String paid,
    long overdueCount,
    String overdueTotal,
    List<ExpenseView> expenses) {
  public static ExpenseMonthView of(ExpenseService.Payables payables, Expense.Situation situation) {
    return new ExpenseMonthView(
        payables.month().toString(),
        situation == null ? null : situation.name(),
        ExpenseView.money(payables.totals().toPay()),
        ExpenseView.money(payables.totals().paid()),
        payables.overdue().count(),
        ExpenseView.money(payables.overdue().total()),
        payables.shown().stream().map(item -> ExpenseView.of(item, payables.today())).toList());
  }
}
