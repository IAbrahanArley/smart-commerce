package com.smartcommerce.finance.controllers;

import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.dto.request.CancelExpenseRequest;
import com.smartcommerce.finance.dto.request.ExpenseRequest;
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
 * Store OWNER manual expenses (SC-020). Session and CSRF on mutations; responses never cached.
 * There is no delete: corrections cancel with a reason and record a new expense.
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

  @GetMapping
  public ResponseEntity<ExpenseMonthView> month(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String month,
      Authentication actor) {
    YearMonth period = month(month);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ExpenseMonthView.of(
                period, expenses.month(UUID.fromString(actor.getName()), tenantId, period)));
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
            request.paidOn(),
            expenses.today());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ExpenseView.of(expenses.create(UUID.fromString(actor.getName()), tenantId, input)));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/{expenseId}/cancel")
  public ResponseEntity<ExpenseView> cancel(
      @PathVariable UUID tenantId,
      @PathVariable UUID expenseId,
      @RequestBody CancelExpenseRequest request,
      Authentication actor) {
    String reason = ExpenseInput.line(request == null ? null : request.reason(), 300);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ExpenseView.of(
                expenses.cancel(UUID.fromString(actor.getName()), tenantId, expenseId, reason)));
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
}
