package com.smartcommerce.finance.services;

import com.smartcommerce.customers.domain.StoreCustomer;
import com.smartcommerce.customers.services.StoreCustomerService;
import com.smartcommerce.finance.domain.CashSession;
import com.smartcommerce.finance.domain.Credit;
import com.smartcommerce.finance.domain.CreditException;
import com.smartcommerce.finance.domain.FinanceAccount;
import com.smartcommerce.finance.domain.PageCursor;
import com.smartcommerce.finance.repositories.AccountRepository;
import com.smartcommerce.finance.repositories.CashSessionRepository;
import com.smartcommerce.finance.repositories.CreditRepository;
import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store credit ("crediário", SC-055, ADR-0019 C9).
 *
 * <ul>
 *   <li>A credit sale at the counter becomes 1 to 12 monthly installments, equal cents with the
 *       last one absorbing the rest; it is refused above the customer's limit, counting what they
 *       already owe.
 *   <li>The customer pays any amount up to what they owe, into a drawer's open cash session or a
 *       bank account (never Mercado Pago); it pays the oldest installments first. A sale settles in
 *       the ledger when all its installments are received.
 *   <li>The OWNER and the store's employees receive. An employee sees only the customer being
 *       served: name, masked phone, what is open and overdue and where the money can go; the
 *       store's list and totals, the history and the limit are the OWNER's (decided on 2026-11-04).
 *       Anyone else gets one 404. Writes lock the store.
 * </ul>
 */
@Service
public class CreditService {
  private static final String RECEIPT = "Recebimento de crediário";

  private final CreditRepository credit;
  private final CashSessionRepository sessions;
  private final AccountRepository accounts;
  private final FinanceLedgerRepository ledger;
  private final StoreCustomerService customers;
  private final Clock clock;

  public CreditService(
      CreditRepository credit,
      CashSessionRepository sessions,
      AccountRepository accounts,
      FinanceLedgerRepository ledger,
      StoreCustomerService customers,
      Clock clock) {
    this.credit = credit;
    this.sessions = sessions;
    this.accounts = accounts;
    this.ledger = ledger;
    this.customers = customers;
    this.clock = clock;
  }

  // ---- For the counter sale (orders module): the caller authorized the actor and locked the
  // store.

  public enum Check {
    OK,
    MISSING,
    LIMIT
  }

  /** Whether the customer of the store may buy {@code total} more on credit. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Check check(UUID tenant, UUID customer, BigDecimal total) {
    var found = customers.find(tenant, customer);
    if (found.isEmpty()) return Check.MISSING;
    BigDecimal limit = found.get().creditLimit();
    if (limit != null && credit.balance(tenant, customer).add(total).compareTo(limit) > 0)
      return Check.LIMIT;
    return Check.OK;
  }

  /** The installments of a credit sale: monthly from {@code firstDueOn}. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recordSale(
      UUID tenant, UUID sale, UUID customer, BigDecimal total, int count, LocalDate firstDueOn) {
    Instant now = now();
    var amounts = Credit.split(total, count);
    for (int index = 0; index < count; index++)
      credit.insertInstallment(
          tenant, sale, customer, index + 1, firstDueOn.plusMonths(index), amounts.get(index), now);
  }

  /** The first installment's due date of a credit sale (to tell a retry from another sale). */
  @Transactional(readOnly = true)
  public LocalDate firstDueOn(UUID tenant, UUID sale) {
    return credit.firstDueOn(tenant, sale).orElseThrow();
  }

  /** Whether some receipt already paid part of the credit sale (so it cannot be cancelled). */
  @Transactional(readOnly = true)
  public boolean received(UUID tenant, UUID sale) {
    return credit.saleReceived(tenant, sale);
  }

  // ---- Admin.

  public record Destinations(List<CashSession> sessions, List<FinanceAccount> banks) {}

  /**
   * One customer as the viewer may see them: the OWNER gets the whole record, every open
   * installment and one page of history; an employee gets the open installments only.
   */
  public record CustomerView(
      boolean owner,
      StoreCustomer customer,
      BigDecimal open,
      BigDecimal overdue,
      List<Credit.Installment> installments,
      List<Credit.Event> history,
      PageCursor nextCursor,
      Destinations destinations,
      LocalDate today) {}

  @Transactional
  public CustomerView customer(UUID actor, UUID tenant, UUID id, PageCursor cursor) {
    boolean owner = owner(member(actor, tenant, false));
    var customer = customers.find(tenant, id).orElseThrow(CreditService::missing);
    LocalDate today = today();
    var open = credit.installments(tenant, id, true);
    BigDecimal balance = sum(open, false, today);
    BigDecimal overdue = sum(open, true, today);
    List<Credit.Event> history = List.of();
    PageCursor next = null;
    if (owner) {
      var page = credit.history(tenant, id, cursor, PageCursor.PAGE_SIZE + 1);
      boolean more = page.size() > PageCursor.PAGE_SIZE;
      history = more ? page.subList(0, PageCursor.PAGE_SIZE) : page;
      if (more) next = new PageCursor(null, history.getLast().at(), history.getLast().id());
    }
    return new CustomerView(
        owner, customer, balance, overdue, open, history, next, destinations(tenant), today);
  }

  public record Overview(
      Credit.Totals totals,
      List<Credit.CustomerLine> customers,
      PageCursor nextCursor,
      LocalDate today) {}

  /** The OWNER's list of customers, newest first, all, with something open or overdue. */
  @Transactional
  public Overview overview(UUID actor, UUID tenant, String filter, PageCursor cursor) {
    if (!owner(member(actor, tenant, false))) throw missing();
    if (filter != null && !Set.of("ALL", "OPEN", "OVERDUE").contains(filter))
      throw new IllegalArgumentException("Invalid filter");
    LocalDate today = today();
    var page = credit.customers(tenant, today, filter, cursor, PageCursor.PAGE_SIZE + 1);
    boolean more = page.size() > PageCursor.PAGE_SIZE;
    var shown = more ? page.subList(0, PageCursor.PAGE_SIZE) : page;
    return new Overview(
        credit.totals(tenant, today),
        shown,
        more ? new PageCursor(null, shown.getLast().createdAt(), shown.getLast().id()) : null,
        today);
  }

  /** A receipt: into the drawer of an open cash session, or into a bank account of the store. */
  public record ReceiptInput(
      UUID requestId, BigDecimal amount, UUID cashSessionId, UUID accountId) {
    public ReceiptInput {
      if (requestId == null
          || amount == null
          || amount.signum() <= 0
          || amount.scale() > 2
          || (cashSessionId == null) == (accountId == null))
        throw new IllegalArgumentException("Invalid receipt");
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CustomerView receive(UUID actor, UUID tenant, UUID customer, ReceiptInput input) {
    member(actor, tenant, true);
    customers.find(tenant, customer).orElseThrow(CreditService::missing);
    var previous = credit.receiptByRequest(tenant, input.requestId());
    if (previous.isPresent()) {
      var stored = previous.get();
      if (!stored.customerId().equals(customer)
          || stored.amount().compareTo(input.amount()) != 0
          || !Objects.equals(stored.cashSessionId(), input.cashSessionId())
          || (input.accountId() != null && !stored.accountId().equals(input.accountId())))
        throw new CreditException(CreditException.Reason.REQUEST_CONFLICT);
      return customer(actor, tenant, customer, null);
    }
    UUID account = input.accountId();
    UUID session = input.cashSessionId();
    if (session != null) {
      var found = sessions.lock(tenant, session).orElseThrow(CreditService::missing);
      if (!found.open()) throw new CreditException(CreditException.Reason.SESSION_CLOSED);
      account = found.accountId();
    } else {
      var found = accounts.account(tenant, account).orElseThrow(CreditService::missing);
      // A drawer only through its open session (the blind close counts it); Mercado Pago moves
      // only with the provider.
      if (!"BANK".equals(found.type()))
        throw new CreditException(CreditException.Reason.ACCOUNT_NOT_ALLOWED);
    }
    var open = credit.installments(tenant, customer, true);
    BigDecimal owed =
        open.stream().map(Credit.Installment::open).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (input.amount().compareTo(owed) > 0)
      throw new CreditException(CreditException.Reason.OVERPAYMENT);

    Instant now = now();
    LocalDate today = today();
    UUID receipt = UUID.randomUUID();
    if (!credit.insertReceipt(
        tenant,
        receipt,
        input.requestId(),
        customer,
        input.amount(),
        account,
        session,
        today,
        actor,
        now)) throw new CreditException(CreditException.Reason.REQUEST_CONFLICT);
    BigDecimal left = input.amount();
    Set<UUID> touched = new LinkedHashSet<>();
    for (var installment : open) {
      if (left.signum() == 0) break;
      BigDecimal part = left.min(installment.open());
      credit.insertAllocation(tenant, receipt, installment.id(), part);
      touched.add(installment.saleId());
      left = left.subtract(part);
    }
    accounts.insertCreditMovement(
        tenant,
        account,
        "credit-receipt:" + receipt,
        input.amount(),
        today,
        RECEIPT,
        receipt,
        session,
        actor,
        now);
    for (UUID sale : touched)
      if (credit.salePaid(tenant, sale)) ledger.settle(tenant, "counter-sale:" + sale, now);
    return customer(actor, tenant, customer, null);
  }

  private Destinations destinations(UUID tenant) {
    return new Destinations(
        sessions.openSessions(tenant),
        accounts.accounts(tenant).stream().filter(found -> "BANK".equals(found.type())).toList());
  }

  private static BigDecimal sum(
      List<Credit.Installment> open, boolean overdueOnly, LocalDate today) {
    return open.stream()
        .filter(installment -> !overdueOnly || installment.overdue(today))
        .map(Credit.Installment::open)
        .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
  }

  private String member(UUID actor, UUID tenant, boolean write) {
    return sessions.lockMember(actor, tenant, write).orElseThrow(CreditService::missing);
  }

  private static boolean owner(String role) {
    return "OWNER".equals(role);
  }

  private static CreditException missing() {
    return new CreditException(CreditException.Reason.MISSING);
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ExpenseService.ZONE));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
