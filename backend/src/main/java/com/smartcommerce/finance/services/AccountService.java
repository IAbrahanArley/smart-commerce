package com.smartcommerce.finance.services;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartcommerce.finance.domain.AccountMovement;
import com.smartcommerce.finance.domain.FinanceAccount;
import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.finance.domain.MovementInput;
import com.smartcommerce.finance.repositories.AccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where the store's money is (SC-050, ADR-0019 C2, C3).
 *
 * <ul>
 *   <li>Only the store's OWNER reads or writes; the tenant comes from the route and is honoured
 *       only through his membership, anything else is one 404.
 *   <li>Every store has a default drawer ("Caixa da loja") and, once Mercado Pago is connected, a
 *       Mercado Pago account; both appear on first use. The OWNER adds drawers and bank accounts.
 *   <li>The Mercado Pago account takes no manual adjustment or transfer: it will follow the
 *       provider's own facts (SC-018).
 *   <li>A balance is the sum of the account's movements. Movements are never changed or deleted: a
 *       correction is an opposite adjustment with a reason.
 *   <li>A transfer writes its two movements in one transaction, so it never stays half done. The
 *       request id makes a retry the same account, adjustment or transfer, and a different body
 *       under it a conflict.
 * </ul>
 */
@Service
public class AccountService {
  static final int MAX_ACCOUNTS = 20;
  static final int STATEMENT_DAYS = 90;
  static final int MAX_STATEMENT_DAYS = 366;
  static final int MAX_STATEMENT_ROWS = 500;
  private static final LocalDate FLOOR = LocalDate.of(2000, 1, 1);

  private final AccountRepository accounts;
  private final Clock clock;

  public AccountService(AccountRepository accounts, Clock clock) {
    this.accounts = accounts;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(ExpenseService.ZONE));
  }

  @Transactional
  public List<FinanceAccount> list(UUID actor, UUID tenant) {
    requireOwner(actor, tenant, false);
    ensureDefaults(tenant);
    return accounts.accounts(tenant);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public FinanceAccount create(UUID actor, UUID tenant, MovementInput.NewAccount input) {
    requireOwner(actor, tenant, true);
    ensureDefaults(tenant);
    var previous = accounts.accountByRequest(tenant, input.requestId());
    if (previous.isPresent()) {
      if (!previous.get().type().equals(input.type())
          || !previous.get().name().equals(input.name()))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
      return previous.get();
    }
    if (accounts.count(tenant) >= MAX_ACCOUNTS)
      throw new FinanceException(FinanceException.Reason.ACCOUNT_LIMIT);
    UUID id = UUID.randomUUID();
    accounts.insertAccount(
        tenant, id, input.requestId(), input.type(), input.name(), actor, clock.instant());
    return accounts.account(tenant, id).orElseThrow();
  }

  public record Statement(
      FinanceAccount account,
      LocalDate from,
      LocalDate to,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openingBalance,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closingBalance,
      List<AccountMovement> movements,
      boolean truncated) {}

  /**
   * The movements of a period (the last 90 days by default, at most 366 days). Not read-only: the
   * OWNER check locks the store FOR SHARE, which PostgreSQL refuses in a read-only transaction.
   */
  @Transactional
  public Statement statement(UUID actor, UUID tenant, UUID account, String from, String to) {
    requireOwner(actor, tenant, false);
    FinanceAccount found = account(tenant, account);
    LocalDate end = to == null || to.isBlank() ? today() : parse(to);
    LocalDate start =
        from == null || from.isBlank() ? end.minusDays(STATEMENT_DAYS - 1) : parse(from);
    if (start.isBefore(FLOOR)
        || end.isAfter(today().plusYears(1))
        || start.isAfter(end)
        || ChronoUnit.DAYS.between(start, end) >= MAX_STATEMENT_DAYS)
      throw new IllegalArgumentException("Invalid period");
    BigDecimal opening = accounts.balanceBefore(tenant, account, start).setScale(2);
    BigDecimal closing = accounts.balanceBefore(tenant, account, end.plusDays(1)).setScale(2);
    List<AccountMovement> rows =
        accounts.statement(tenant, account, start, end, MAX_STATEMENT_ROWS + 1);
    boolean truncated = rows.size() > MAX_STATEMENT_ROWS;
    return new Statement(
        found,
        start,
        end,
        opening,
        closing,
        truncated ? rows.subList(0, MAX_STATEMENT_ROWS) : rows,
        truncated);
  }

  /** A signed correction of the balance (the opening balance included), with its reason. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public FinanceAccount adjust(
      UUID actor, UUID tenant, UUID account, MovementInput.Adjustment input) {
    requireOwner(actor, tenant, true);
    manual(account(tenant, account));
    String key = "adjustment:" + input.requestId();
    if (!accounts.insertMovement(
        tenant,
        account,
        key,
        "ADJUSTMENT",
        input.amount(),
        input.occurredOn(),
        input.reason(),
        null,
        actor,
        clock.instant())) {
      var stored = accounts.movement(tenant, key).orElseThrow();
      if (!stored.account().equals(account)
          || stored.amount().compareTo(input.amount()) != 0
          || !stored.occurredOn().equals(input.occurredOn())
          || !Objects.equals(stored.description(), input.reason()))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
    }
    return accounts.account(tenant, account).orElseThrow();
  }

  /** Moves money between two accounts of the store: two movements, one transaction. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public List<FinanceAccount> transfer(UUID actor, UUID tenant, MovementInput.Transfer input) {
    requireOwner(actor, tenant, true);
    if (input.from().equals(input.to()))
      throw new FinanceException(FinanceException.Reason.SAME_ACCOUNT);
    manual(account(tenant, input.from()));
    manual(account(tenant, input.to()));
    String out = "transfer:" + input.requestId() + ":out";
    UUID transfer = UUID.randomUUID();
    Instant now = clock.instant();
    if (accounts.insertMovement(
        tenant,
        input.from(),
        out,
        "TRANSFER_OUT",
        input.amount().negate(),
        input.occurredOn(),
        input.note(),
        transfer,
        actor,
        now)) {
      accounts.insertMovement(
          tenant,
          input.to(),
          "transfer:" + input.requestId() + ":in",
          "TRANSFER_IN",
          input.amount(),
          input.occurredOn(),
          input.note(),
          transfer,
          actor,
          now);
    } else {
      var stored = accounts.movement(tenant, out).orElseThrow();
      var incoming =
          accounts.movement(tenant, "transfer:" + input.requestId() + ":in").orElseThrow();
      if (!stored.account().equals(input.from())
          || !incoming.account().equals(input.to())
          || stored.amount().compareTo(input.amount().negate()) != 0
          || !stored.occurredOn().equals(input.occurredOn())
          || !Objects.equals(stored.description(), input.note()))
        throw new FinanceException(FinanceException.Reason.REQUEST_CONFLICT);
    }
    return accounts.accounts(tenant);
  }

  private void ensureDefaults(UUID tenant) {
    Instant now = clock.instant();
    accounts.ensureSystem(tenant, "DEFAULT_CASH", "CASH", "Caixa da loja", now);
    if (accounts.providerConnected(tenant))
      accounts.ensureSystem(tenant, "MERCADO_PAGO", "PAYMENT_PROVIDER", "Mercado Pago", now);
  }

  /**
   * The Mercado Pago account is read-only for the OWNER (decided on 2026-11-01): its money moves
   * only with the provider's own facts and reconciliation (SC-018), never by hand.
   */
  private static void manual(FinanceAccount account) {
    if ("MERCADO_PAGO".equals(account.systemKey()))
      throw new FinanceException(FinanceException.Reason.PROVIDER_READ_ONLY);
  }

  private FinanceAccount account(UUID tenant, UUID account) {
    return accounts
        .account(tenant, account)
        .orElseThrow(() -> new FinanceException(FinanceException.Reason.MISSING));
  }

  private void requireOwner(UUID actor, UUID tenant, boolean write) {
    if (!accounts.lockOwnedStore(actor, tenant, write))
      throw new FinanceException(FinanceException.Reason.MISSING);
  }

  private static LocalDate parse(String value) {
    try {
      return LocalDate.parse(value);
    } catch (java.time.format.DateTimeParseException invalid) {
      throw new IllegalArgumentException("Invalid date");
    }
  }
}
