package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.Expense;
import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.ExpensePayment;
import com.smartcommerce.finance.dto.request.CancelExpenseRequest;
import com.smartcommerce.finance.dto.request.ExpenseRequest;
import com.smartcommerce.finance.dto.request.PayExpenseRequest;
import com.smartcommerce.finance.dto.response.ExpenseMonthView;
import com.smartcommerce.finance.dto.response.ExpenseView;
import com.smartcommerce.finance.services.ExpenseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Store OWNER expenses and payables (SC-020, SC-051). Session and CSRF on mutations; responses
 * never cached. There is no delete: corrections cancel with a reason and record a new expense.
 */
@SecurityRequirement(name = "session")
@Tag(name = "Finance")
@RestController
@RequestMapping("/api/tenants/{tenantId}/expenses")
public final class ExpenseController {
  private final ExpenseService expenses;

  public ExpenseController(ExpenseService expenses) {
    this.expenses = expenses;
  }

  /**
   * Payables falling due in {@code month} (the current one by default), optionally only one {@code
   * situation}: OPEN, OVERDUE (past due bills of any month), PAID or CANCELLED.
   */
  @GetMapping
  public ResponseEntity<ExpenseMonthView> month(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String month,
      @RequestParam(required = false) String situation,
      Authentication actor) {
    YearMonth period = month(month);
    Expense.Situation filter = situation(situation);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ExpenseMonthView.of(
                expenses.month(UUID.fromString(actor.getName()), tenantId, period, filter),
                filter));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping
  public ResponseEntity<ExpenseView> create(
      @PathVariable UUID tenantId, @RequestBody ExpenseRequest request, Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    ExpenseInput input =
        ExpenseInput.of(
            request.requestId(),
            request.category(),
            request.kind(),
            request.description(),
            request.amount(),
            request.competenceDate(),
            request.dueDate(),
            request.supplier(),
            request.documentNumber(),
            request.installments(),
            request.recurrence(),
            request.paidOn(),
            request.paidAccountId(),
            expenses.today());
    return view(expenses.create(UUID.fromString(actor.getName()), tenantId, input));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/{expenseId}/pay")
  public ResponseEntity<ExpenseView> pay(
      @PathVariable UUID tenantId,
      @PathVariable UUID expenseId,
      @RequestBody PayExpenseRequest request,
      Authentication actor) {
    if (request == null) throw new IllegalArgumentException("Missing body");
    ExpensePayment payment =
        ExpensePayment.of(
            request.paidOn(),
            request.accountId(),
            request.surcharge(),
            request.discount(),
            expenses.today());
    return view(expenses.pay(UUID.fromString(actor.getName()), tenantId, expenseId, payment));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/{expenseId}/cancel")
  public ResponseEntity<ExpenseView> cancel(
      @PathVariable UUID tenantId,
      @PathVariable UUID expenseId,
      @RequestBody CancelExpenseRequest request,
      Authentication actor) {
    String reason = ExpenseInput.line(request == null ? null : request.reason(), 300);
    return view(expenses.cancel(UUID.fromString(actor.getName()), tenantId, expenseId, reason));
  }

  private ResponseEntity<ExpenseView> view(Expense expense) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ExpenseView.of(expense, expenses.today()));
  }

  private YearMonth month(String value) {
    if (value == null || value.isBlank()) return YearMonth.from(expenses.today());
    if (!value.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException("Invalid month");
    try {
      return YearMonth.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new IllegalArgumentException("Invalid month");
    }
  }

  private static Expense.Situation situation(String value) {
    if (value == null || value.isBlank()) return null;
    for (Expense.Situation item : Expense.Situation.values())
      if (item.name().equals(value)) return item;
    throw new IllegalArgumentException("Invalid situation");
  }
}
