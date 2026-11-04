package com.smartcommerce.finance.services;

import com.smartcommerce.finance.domain.CashInput;
import com.smartcommerce.finance.domain.CashSession;
import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.finance.domain.PageCursor;
import com.smartcommerce.finance.repositories.AccountRepository;
import com.smartcommerce.finance.repositories.CashSessionRepository;
import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The store's cash register (SC-052, ADR-0019 C5): sessions of its drawers.
 *
 * <ul>
 *   <li>The OWNER and the store's employees (STAFF) open, move and close; only the OWNER reopens,
 *       reads the history and ever sees the expected amount. Anyone else gets one 404.
 *   <li>The drawer balance is the cash in it. Opening declares the counted change float: a first
 *       count on an untouched drawer is its opening balance, any later mismatch a cash difference.
 *   <li>Closing is blind: the counted amount minus the drawer balance is the difference, recorded
 *       as a movement and as a CASH_DIFFERENCE ledger line of its own (decided on 2026-11-03).
 *   <li>A drop or top-up with another account is a transfer; without one it is the owner's
 *       withdrawal or contribution, outside the result (decided on 2026-11-03). An expense paid by
 *       the drawer is a payable recorded already paid by it.
 *   <li>Writes lock the store and the session; the database also refuses a second open session of a
 *       drawer, a movement in a closed session and any change to a closed session.
 * </ul>
 */
@Service
public class CashSessionService {
  private final CashSessionRepository sessions;
  private final AccountRepository accounts;
  private final FinanceLedgerRepository ledger;
  private final ExpenseService expenses;
  private final Clock clock;

  public CashSessionService(
      CashSessionRepository sessions,
      AccountRepository accounts,
      FinanceLedgerRepository ledger,
      ExpenseService expenses,
      Clock clock) {
    this.sessions = sessions;
    this.accounts = accounts;
    this.ledger = ledger;
    this.expenses = expenses;
    this.clock = clock;
  }

  /** A session as its viewer sees it: the expected amount and difference only for the OWNER. */
  public record SessionDetail(
      CashSession session, List<CashSession.Movement> movements, boolean owner) {}

  public record Overview(
      String role,
      List<DrawerState> drawers,
      List<CashSessionRepository.Destination> destinations) {}

  public record DrawerState(CashSessionRepository.Drawer drawer, SessionDetail session) {}

  /** The store's drawers, each with its open session, and the accounts drops and top-ups use. */
  @Transactional
  public Overview overview(UUID actor, UUID tenant) {
    String role = member(actor, tenant, true);
    accounts.ensureSystem(tenant, "DEFAULT_CASH", "CASH", "Caixa da loja", clock.instant());
    boolean owner = "OWNER".equals(role);
    List<DrawerState> drawers =
        sessions.drawers(tenant).stream()
            .map(
                drawer ->
                    new DrawerState(
                        drawer,
                        sessions
                            .openFor(tenant, drawer.id())
                            .map(open -> detail(tenant, open, owner))
                            .orElse(null)))
            .toList();
    return new Overview(role, drawers, sessions.destinations(tenant));
  }

  /** One session; an employee only reaches open ones. */
  @Transactional
  public SessionDetail session(UUID actor, UUID tenant, UUID id) {
    boolean owner = "OWNER".equals(member(actor, tenant, false));
    CashSession found = sessions.byId(tenant, id).orElseThrow(CashSessionService::missing);
    if (!owner && !found.open()) throw missing();
    return detail(tenant, found, owner);
  }

  public record History(List<CashSession> sessions, PageCursor nextCursor) {}

  /**
   * The OWNER's history, one page of sessions newest first (after {@code cursor} when given), with
   * expected amounts and differences; {@code nextCursor} is null on the last page.
   */
  @Transactional
  public History history(UUID actor, UUID tenant, PageCursor cursor) {
    requireOwner(member(actor, tenant, false));
    var page = sessions.page(tenant, cursor, PageCursor.PAGE_SIZE + 1);
    if (page.size() <= PageCursor.PAGE_SIZE) return new History(page, null);
    var shown = page.subList(0, PageCursor.PAGE_SIZE);
    var last = shown.getLast();
    return new History(shown, new PageCursor(null, last.openedAt(), last.id()));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SessionDetail open(UUID actor, UUID tenant, CashInput.Open input) {
    boolean owner = "OWNER".equals(member(actor, tenant, true));
    accounts.ensureSystem(tenant, "DEFAULT_CASH", "CASH", "Caixa da loja", clock.instant());
    Optional<CashSession> previous = sessions.byRequest(tenant, input.requestId());
    if (previous.isPresent()) {
      if (!previous.get().accountId().equals(input.accountId())
          || previous.get().openingAmount().compareTo(input.amount()) != 0)
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
      // An employee only ever reaches open sessions, also through a replayed request.
      if (!owner && !previous.get().open()) throw missing();
      return detail(tenant, previous.get(), owner);
    }
    drawer(tenant, input.accountId());
    if (sessions.openFor(tenant, input.accountId()).isPresent())
      throw new FinanceException(FinanceException.Reason.SESSION_OPEN);
    UUID id = UUID.randomUUID();
    Instant now = now();
    boolean untouched = accounts.untouched(tenant, input.accountId());
    BigDecimal difference = input.amount().subtract(accounts.balance(tenant, input.accountId()));
    insert(
        tenant, id, input.accountId(), input.requestId(), input.amount(), actor, now, null, null);
    if (difference.signum() != 0) {
      if (untouched)
        accounts.insertSessionMovement(
            tenant,
            input.accountId(),
            "cash-opening:" + id,
            "ADJUSTMENT",
            difference,
            today(),
            "Saldo inicial informado na abertura do caixa",
            null,
            id,
            actor,
            now);
      else
        difference(
            tenant,
            id,
            input.accountId(),
            "cash-opening:" + id,
            difference,
            actor,
            now,
            "Diferença na abertura do caixa");
    }
    return detail(tenant, sessions.byId(tenant, id).orElseThrow(), owner);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SessionDetail move(UUID actor, UUID tenant, UUID id, CashInput.Movement input) {
    boolean owner = "OWNER".equals(member(actor, tenant, true));
    CashSession session = sessions.lock(tenant, id).orElseThrow(CashSessionService::missing);
    String key = "cash-move:" + input.requestId();
    if ("EXPENSE".equals(input.type())) {
      if (!session.open()) throw new FinanceException(FinanceException.Reason.SESSION_CLOSED);
      LocalDate today = today();
      String day = today.toString();
      expenses.recordFromDrawer(
          actor,
          tenant,
          ExpenseInput.of(
              input.requestId().toString(),
              input.category(),
              fixed(input.category()) ? "FIXED" : "VARIABLE",
              input.reason(),
              input.amount().toPlainString(),
              day,
              day,
              null,
              null,
              1,
              "NONE",
              day,
              session.accountId().toString(),
              today),
          id);
      return detail(tenant, sessions.byId(tenant, id).orElseThrow(), owner);
    }
    boolean drop = "DROP".equals(input.type());
    String drawerKey = key + (input.accountId() == null ? "" : drop ? ":out" : ":in");
    var stored = accounts.movement(tenant, drawerKey);
    if (stored.isPresent()) {
      BigDecimal signed = drop ? input.amount().negate() : input.amount();
      if (!stored.get().account().equals(session.accountId())
          || stored.get().amount().compareTo(signed) != 0
          || !Objects.equals(stored.get().description(), input.reason()))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
      return detail(tenant, session, owner);
    }
    if (!session.open()) throw new FinanceException(FinanceException.Reason.SESSION_CLOSED);
    Instant now = now();
    LocalDate today = today();
    if (input.accountId() == null) {
      accounts.insertSessionMovement(
          tenant,
          session.accountId(),
          drawerKey,
          drop ? "OWNER_WITHDRAWAL" : "OWNER_CONTRIBUTION",
          drop ? input.amount().negate() : input.amount(),
          today,
          input.reason(),
          null,
          id,
          actor,
          now);
    } else {
      if (input.accountId().equals(session.accountId()))
        throw new FinanceException(FinanceException.Reason.SAME_ACCOUNT);
      var other =
          accounts.account(tenant, input.accountId()).orElseThrow(CashSessionService::missing);
      if ("MERCADO_PAGO".equals(other.systemKey()))
        throw new FinanceException(FinanceException.Reason.PROVIDER_READ_ONLY);
      UUID transfer = UUID.randomUUID();
      accounts.insertSessionMovement(
          tenant,
          session.accountId(),
          drawerKey,
          drop ? "TRANSFER_OUT" : "TRANSFER_IN",
          drop ? input.amount().negate() : input.amount(),
          today,
          input.reason(),
          transfer,
          id,
          actor,
          now);
      accounts.insertSessionMovement(
          tenant,
          other.id(),
          key + (drop ? ":in" : ":out"),
          drop ? "TRANSFER_IN" : "TRANSFER_OUT",
          drop ? input.amount() : input.amount().negate(),
          today,
          input.reason(),
          transfer,
          null,
          actor,
          now);
    }
    return detail(tenant, sessions.byId(tenant, id).orElseThrow(), owner);
  }

  /** Blind close; the same request again returns the closed session, another one is refused. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SessionDetail close(UUID actor, UUID tenant, UUID id, CashInput.Close input) {
    boolean owner = "OWNER".equals(member(actor, tenant, true));
    CashSession session = sessions.lock(tenant, id).orElseThrow(CashSessionService::missing);
    if (!session.open()) {
      if (input.requestId().equals(session.closeRequestId())
          && input.counted().compareTo(session.countedAmount()) == 0)
        return detail(tenant, session, owner);
      throw new FinanceException(FinanceException.Reason.SESSION_CLOSED);
    }
    Instant now = now();
    BigDecimal expected = accounts.balance(tenant, session.accountId());
    BigDecimal difference = input.counted().subtract(expected);
    if (difference.signum() != 0)
      difference(
          tenant,
          id,
          session.accountId(),
          "cash-closing:" + id,
          difference,
          actor,
          now,
          "Diferença no fechamento do caixa");
    sessions.close(tenant, id, input.requestId(), input.counted(), expected, actor, now);
    return detail(tenant, sessions.byId(tenant, id).orElseThrow(), owner);
  }

  /**
   * The OWNER reopens a closed session with a reason: a new open session linked to it, starting
   * from the counted amount, with no new count. Reopening again returns the same new session.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SessionDetail reopen(UUID actor, UUID tenant, UUID id, String reason) {
    requireOwner(member(actor, tenant, true));
    CashSession closed = sessions.lock(tenant, id).orElseThrow(CashSessionService::missing);
    if (closed.open()) throw new FinanceException(FinanceException.Reason.SESSION_OPEN);
    var again = sessions.reopenOf(tenant, id);
    if (again.isPresent()) return detail(tenant, again.get(), true);
    if (sessions.openFor(tenant, closed.accountId()).isPresent())
      throw new FinanceException(FinanceException.Reason.SESSION_OPEN);
    UUID reopened = UUID.randomUUID();
    insert(
        tenant,
        reopened,
        closed.accountId(),
        // Random: a derived id could be taken first by an opening forged with it. Reopening twice
        // is still one session (reopenOf above and the unique reopened_from).
        UUID.randomUUID(),
        closed.countedAmount(),
        actor,
        now(),
        id,
        reason);
    return detail(tenant, sessions.byId(tenant, reopened).orElseThrow(), true);
  }

  private void insert(
      UUID tenant,
      UUID id,
      UUID account,
      UUID request,
      BigDecimal opening,
      UUID actor,
      Instant now,
      UUID reopenedFrom,
      String reason) {
    try {
      if (!sessions.insert(tenant, id, account, request, opening, actor, now, reopenedFrom, reason))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
    } catch (DuplicateKeyException open) {
      // The partial unique index: another session of this drawer opened meanwhile.
      throw new FinanceException(FinanceException.Reason.SESSION_OPEN);
    }
  }

  private void difference(
      UUID tenant,
      UUID session,
      UUID account,
      String key,
      BigDecimal amount,
      UUID actor,
      Instant now,
      String description) {
    accounts.insertSessionMovement(
        tenant,
        account,
        key,
        "CASH_DIFFERENCE",
        amount,
        today(),
        description,
        null,
        session,
        actor,
        now);
    ledger.appendCashDifference(
        tenant, "cash-difference:" + key, amount, ExpenseService.startOf(today()), session, now);
  }

  /**
   * An employee never sees a cash difference: with the counted amount it would give the expected
   * amount away (blind close) and, at opening, the drawer balance.
   */
  private SessionDetail detail(UUID tenant, CashSession session, boolean owner) {
    List<CashSession.Movement> movements = sessions.movements(tenant, session.id());
    if (!owner)
      movements =
          movements.stream().filter(item -> !"CASH_DIFFERENCE".equals(item.kind())).toList();
    return new SessionDetail(session, movements, owner);
  }

  private void drawer(UUID tenant, UUID account) {
    if (sessions.drawers(tenant).stream().noneMatch(drawer -> drawer.id().equals(account)))
      throw missing();
  }

  /** Rent and payroll are fixed costs; anything else paid from the drawer is variable. */
  private static boolean fixed(String category) {
    return "RENT".equals(category) || "PEOPLE".equals(category);
  }

  private String member(UUID actor, UUID tenant, boolean write) {
    return sessions.lockMember(actor, tenant, write).orElseThrow(CashSessionService::missing);
  }

  private static void requireOwner(String role) {
    if (!"OWNER".equals(role)) throw missing();
  }

  private static FinanceException missing() {
    return new FinanceException(FinanceException.Reason.MISSING);
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ExpenseService.ZONE));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
