package com.smartcommerce.finance.services;

import com.smartcommerce.finance.domain.Expense;
import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.ExpensePayment;
import com.smartcommerce.finance.domain.FinanceAccount;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.finance.repositories.AccountRepository;
import com.smartcommerce.finance.repositories.ExpenseRepository;
import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
 * Expenses and payables of the store OWNER (SC-020, SC-051; ADR-0015 F6, F7, ADR-0019 C3, C4).
 *
 * <ul>
 *   <li>The tenant comes from the route but is honoured only through the actor's OWNER membership;
 *       anything else is one 404.
 *   <li>Creating records the expense (or each installment) and its EXPENSE ledger entry in one
 *       transaction; the request id makes a retry or double click the same expense, and a different
 *       body under it a conflict.
 *   <li>Paying records the day and the account in full: the ledger entry gets its settlement,
 *       interest or fine and discount become entries of their own on the payment day, and the money
 *       leaves the account (EXPENSE_PAYMENT). A monthly bill creates the next month's one, once.
 *       The Mercado Pago account pays nothing by hand.
 *   <li>Nothing is deleted: cancelling (to correct) keeps the expense with who, when and why, and
 *       adds an EXPENSE_REVERSAL with the same competence, so every period stays right. A bill paid
 *       from an account also gets its money back on the cancellation day (EXPENSE_REFUND), decided
 *       on 2026-11-02. A correction is a cancellation followed by a new expense.
 * </ul>
 */
@Service
public class ExpenseService {
  /** Calendar of the stores (ADR-0015 F1): days start at midnight in São Paulo. */
  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  static final int MAX_PER_MONTH = 500;

  /** account_movement keeps |amount| below 10^15 (V41). */
  private static final BigDecimal MAX_MOVEMENT = new BigDecimal("1000000000000000");

  private final ExpenseRepository expenses;
  private final FinanceLedgerRepository ledger;
  private final AccountRepository accounts;
  private final Clock clock;

  public ExpenseService(
      ExpenseRepository expenses,
      FinanceLedgerRepository ledger,
      AccountRepository accounts,
      Clock clock) {
    this.expenses = expenses;
    this.ledger = ledger;
    this.accounts = accounts;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** Records an expense (the first installment, when there are several) and returns it. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Expense create(UUID actor, UUID tenant, ExpenseInput input) {
    requireOwner(actor, tenant);
    return createAs(actor, tenant, input, null);
  }

  /**
   * An expense paid with the drawer's cash during a cash session (SC-052): recorded and paid at
   * once, its movement tied to the session. Internal: the caller has already authorized the store's
   * OWNER or employee and locked the open session, inside its own transaction.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Expense recordFromDrawer(UUID actor, UUID tenant, ExpenseInput input, UUID session) {
    if (input.paidOn() == null || input.installments() != 1 || input.monthly())
      throw new IllegalArgumentException("Invalid drawer expense");
    return createAs(actor, tenant, input, session);
  }

  private Expense createAs(UUID actor, UUID tenant, ExpenseInput input, UUID session) {
    Instant now = now();
    List<BigDecimal> amounts = input.installmentAmounts();
    boolean installments = amounts.size() > 1;
    UUID first = UUID.randomUUID();
    var row =
        new ExpenseRepository.NewRow(
            first,
            input.requestId(),
            amounts.get(0),
            input.competenceDate(),
            input.dueDate(),
            installments ? 1 : null,
            null);
    if (!expenses.insert(tenant, input, row, actor, now)) {
      Expense previous = expenses.byRequest(tenant, input.requestId()).orElseThrow();
      if (!input.sameAs(previous))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
      return previous;
    }
    record(tenant, row, now);
    for (int number = 2; number <= amounts.size(); number++) {
      LocalDate due = input.dueDate().plusMonths(number - 1);
      var next =
          new ExpenseRepository.NewRow(
              UUID.randomUUID(),
              derived(input.requestId() + ":" + number),
              amounts.get(number - 1),
              due,
              due,
              number,
              null);
      // Only a request id forged to collide with a derived one can already be there.
      if (!expenses.insert(tenant, input, next, actor, now))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
      record(tenant, next, now);
    }
    if (input.paidOn() != null) {
      Expense created = expenses.lock(tenant, first).orElseThrow();
      settle(tenant, created, ExpensePayment.of(input), actor, now, session);
    }
    return expenses.byId(tenant, first).orElseThrow();
  }

  /** Pays an open or overdue payable in full; the same payment again is returned unchanged. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Expense pay(UUID actor, UUID tenant, UUID id, ExpensePayment payment) {
    requireOwner(actor, tenant);
    Expense expense =
        expenses
            .lock(tenant, id)
            .orElseThrow(() -> new FinanceException(FinanceException.Reason.MISSING));
    if (!expense.active()) throw new FinanceException(FinanceException.Reason.NOT_PAYABLE);
    if (expense.paidOn() != null) {
      if (expense.paidAccountId() != null && payment.sameAs(expense)) return expense;
      throw new FinanceException(FinanceException.Reason.ALREADY_PAID);
    }
    settle(tenant, expense, payment, actor, now(), null);
    return expenses.byId(tenant, id).orElseThrow();
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
    // A paid monthly bill already created next month's one: if still unpaid, it goes too, so a
    // correction (which creates its own successor when paid) never leaves two for one month.
    if (expense.monthly() && expense.paidOn() != null)
      expenses
          .lockSuccessor(tenant, id)
          .filter(next -> next.active() && next.paidOn() == null)
          .ifPresent(
              next ->
                  cancelMirrored(
                      tenant, next, actor, "Conta mensal anterior cancelada: " + reason, now));
    if (expense.paidAccountId() == null) {
      cancelMirrored(tenant, expense, actor, reason, now);
      return expenses.byId(tenant, id).orElseThrow();
    }
    expenses.cancel(tenant, id, actor, reason, now);
    Instant competence = startOf(expense.competenceDate());
    // Paid from an account: the money comes back to it today, and so does the settlement.
    LocalDate today = today();
    Instant back = startOf(today);
    Instant paid = startOf(expense.paidOn());
    ledger.appendExpense(
        tenant,
        "expense-reversal:" + id,
        "EXPENSE_REVERSAL",
        expense.amount(),
        competence,
        back,
        id,
        now);
    if (expense.surcharge().signum() > 0)
      ledger.appendExpense(
          tenant,
          "expense-surcharge-reversal:" + id,
          "EXPENSE_REVERSAL",
          expense.surcharge(),
          paid,
          back,
          id,
          now);
    if (expense.discount().signum() > 0)
      ledger.appendExpense(
          tenant,
          "expense-discount-reversal:" + id,
          "EXPENSE",
          expense.discount().negate(),
          paid,
          back,
          id,
          now);
    accounts.insertExpenseMovement(
        tenant,
        expense.paidAccountId(),
        "expense-refund:" + id,
        "EXPENSE_REFUND",
        expense.paidTotal(),
        today,
        expense.description(),
        id,
        null,
        actor,
        now);
    return expenses.byId(tenant, id).orElseThrow();
  }

  /** Unpaid, or paid before SC-051 with no account: the reversal mirrors the original entry. */
  private void cancelMirrored(
      UUID tenant, Expense expense, UUID actor, String reason, Instant now) {
    expenses.cancel(tenant, expense.id(), actor, ExpenseInput.clip(reason, 300), now);
    ledger.appendExpense(
        tenant,
        "expense-reversal:" + expense.id(),
        "EXPENSE_REVERSAL",
        expense.amount(),
        startOf(expense.competenceDate()),
        expense.paidOn() == null ? null : startOf(expense.paidOn()),
        expense.id(),
        now);
  }

  /**
   * Payables of one due month: the ones shown (all of them, or one situation; past due ones from
   * any month), the month's totals and the store's past-due totals.
   */
  public record Payables(
      YearMonth month,
      LocalDate today,
      List<Expense> shown,
      ExpenseRepository.MonthTotals totals,
      ExpenseRepository.Overdue overdue) {}

  @Transactional(readOnly = true)
  public Payables month(UUID actor, UUID tenant, YearMonth month, Expense.Situation situation) {
    requireOwner(actor, tenant);
    LocalDate today = today();
    LocalDate from = month.atDay(1);
    LocalDate to = month.plusMonths(1).atDay(1);
    List<Expense> shown;
    if (situation == Expense.Situation.OVERDUE)
      shown = expenses.overdue(tenant, today, MAX_PER_MONTH);
    else {
      List<Expense> due = expenses.dueBetween(tenant, from, to, MAX_PER_MONTH);
      shown =
          situation == null
              ? due
              : due.stream().filter(item -> item.situation(today) == situation).toList();
    }
    return new Payables(
        month,
        today,
        shown,
        expenses.monthTotals(tenant, from, to),
        expenses.overdueTotals(tenant, today));
  }

  private void settle(
      UUID tenant, Expense expense, ExpensePayment payment, UUID actor, Instant now, UUID session) {
    FinanceAccount account =
        accounts
            .account(tenant, payment.accountId())
            .orElseThrow(() -> new FinanceException(FinanceException.Reason.MISSING));
    if ("MERCADO_PAGO".equals(account.systemKey()))
      throw new FinanceException(FinanceException.Reason.PROVIDER_READ_ONLY);
    BigDecimal total = expense.amount().add(payment.surcharge()).subtract(payment.discount());
    if (total.signum() <= 0) throw new IllegalArgumentException("Invalid discount");
    if (total.compareTo(MAX_MOVEMENT) >= 0) throw new IllegalArgumentException("Invalid amount");
    if (!expenses.pay(tenant, expense.id(), payment, actor, now))
      throw new FinanceException(FinanceException.Reason.ALREADY_PAID);
    UUID id = expense.id();
    Instant day = startOf(payment.paidOn());
    // Every expense has its unsettled entry; anything else would debit the account silently.
    if (!ledger.settle(tenant, "expense:" + id, day))
      throw new IllegalStateException("Expense entry missing or already settled");
    if (payment.surcharge().signum() > 0)
      ledger.appendExpense(
          tenant,
          "expense-surcharge:" + id,
          "EXPENSE",
          payment.surcharge().negate(),
          day,
          day,
          id,
          now);
    if (payment.discount().signum() > 0)
      ledger.appendExpense(
          tenant,
          "expense-discount:" + id,
          "EXPENSE_REVERSAL",
          payment.discount(),
          day,
          day,
          id,
          now);
    accounts.insertExpenseMovement(
        tenant,
        account.id(),
        "expense-payment:" + id,
        "EXPENSE_PAYMENT",
        total.negate(),
        payment.paidOn(),
        expense.description(),
        id,
        session,
        actor,
        now);
    if (expense.monthly()) next(tenant, expense, actor, now);
  }

  /** The next month's bill of a monthly one, created once (its request id comes from this one). */
  private void next(UUID tenant, Expense expense, UUID actor, Instant now) {
    UUID request = derived("recurrence:" + expense.id());
    var input =
        new ExpenseInput(
            request,
            expense.category(),
            expense.kind(),
            expense.description(),
            expense.amount(),
            expense.competenceDate().plusMonths(1),
            expense.dueDate().plusMonths(1),
            expense.supplier(),
            expense.documentNumber(),
            1,
            "MONTHLY",
            null,
            null);
    var row =
        new ExpenseRepository.NewRow(
            UUID.randomUUID(),
            request,
            expense.amount(),
            input.competenceDate(),
            input.dueDate(),
            null,
            expense.id());
    if (expenses.insert(tenant, input, row, actor, now)) record(tenant, row, now);
  }

  private void record(UUID tenant, ExpenseRepository.NewRow row, Instant now) {
    ledger.appendExpense(
        tenant,
        "expense:" + row.id(),
        "EXPENSE",
        row.amount().negate(),
        startOf(row.competence()),
        null,
        row.id(),
        now);
  }

  /** A stable request id for rows a request creates beyond its first (installments, successor). */
  private static UUID derived(String seed) {
    return UUID.nameUUIDFromBytes(("expense:" + seed).getBytes(StandardCharsets.UTF_8));
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
