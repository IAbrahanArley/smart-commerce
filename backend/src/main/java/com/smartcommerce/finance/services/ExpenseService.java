package com.smartcommerce.finance.services;

import com.smartcommerce.finance.domain.Expense;
import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.finance.repositories.ExpenseRepository;
import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual expenses of the store OWNER (SC-020, ADR-0015 F6, F7).
 *
 * <ul>
 *   <li>The tenant comes from the route but is honoured only through the actor's OWNER membership;
 *       anything else is one 404.
 *   <li>Creating records the expense and its EXPENSE ledger entry in one transaction; the request
 *       id makes a retry or double click the same expense, and a different body under it a
 *       conflict.
 *   <li>Nothing is deleted: cancelling (to correct) keeps the expense with who, when and why, and
 *       adds an EXPENSE_REVERSAL with the same competence and settlement, so every period stays
 *       right. A correction is a cancellation followed by a new expense.
 * </ul>
 */
@Service
public class ExpenseService {
  /** Calendar of the stores (ADR-0015 F1): days start at midnight in São Paulo. */
  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  static final int MAX_PER_MONTH = 500;
  private final ExpenseRepository expenses;
  private final FinanceLedgerRepository ledger;
  private final Clock clock;

  public ExpenseService(ExpenseRepository expenses, FinanceLedgerRepository ledger, Clock clock) {
    this.expenses = expenses;
    this.ledger = ledger;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(ZONE));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Expense create(UUID actor, UUID tenant, ExpenseInput input) {
    requireOwner(actor, tenant);
    Instant now = now();
    UUID id = UUID.randomUUID();
    if (!expenses.insert(tenant, id, input, actor, now)) {
      Expense previous = expenses.byRequest(tenant, input.requestId()).orElseThrow();
      if (!input.sameAs(previous))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
      return previous;
    }
    ledger.appendExpense(
        tenant,
        "expense:" + id,
        "EXPENSE",
        input.amount().negate(),
        startOf(input.competenceDate()),
        input.paidOn() == null ? null : startOf(input.paidOn()),
        id,
        now);
    return expenses.byRequest(tenant, input.requestId()).orElseThrow();
  }

  /** Cancels an expense once; a cancelled one is returned unchanged. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Expense cancel(UUID actor, UUID tenant, UUID id, String reason) {
    requireOwner(actor, tenant);
    Expense expense =
        expenses
            .lock(tenant, id)
            .orElseThrow(() -> new FinanceException(FinanceException.Reason.MISSING));
    if (!expense.active()) return expense;
    Instant now = now();
    expenses.cancel(tenant, id, actor, reason, now);
    ledger.appendExpense(
        tenant,
        "expense-reversal:" + id,
        "EXPENSE_REVERSAL",
        expense.amount(),
        startOf(expense.competenceDate()),
        expense.paidOn() == null ? null : startOf(expense.paidOn()),
        id,
        now);
    return expenses.lock(tenant, id).orElseThrow();
  }

  @Transactional(readOnly = true)
  public List<Expense> month(UUID actor, UUID tenant, YearMonth month) {
    requireOwner(actor, tenant);
    return expenses.between(tenant, month.atDay(1), month.plusMonths(1).atDay(1), MAX_PER_MONTH);
  }

  static Instant startOf(LocalDate day) {
    return day.atStartOfDay(ZONE).toInstant();
  }

  private void requireOwner(UUID actor, UUID tenant) {
    if (!expenses.ownsStore(actor, tenant))
      throw new FinanceException(FinanceException.Reason.MISSING);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
