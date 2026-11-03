package com.smartcommerce.finance.services;

import com.smartcommerce.finance.domain.CashSession;
import com.smartcommerce.finance.repositories.AccountRepository;
import com.smartcommerce.finance.repositories.CashSessionRepository;
import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The money side of counter sales (SC-053, ADR-0019 C6–C8), for the orders module. Internal: the
 * caller has already authorized the store's OWNER or employee and joins its transaction.
 *
 * <ul>
 *   <li>Ledger: SALE (total) and COGS at the sale, plus FEE when a card fee is informed; cash and
 *       Pix settle at once, cards stay receivable until released (SC-055). A cost never settles: no
 *       money moves for it, as with online orders.
 *   <li>Money: cash goes into the drawer of the open cash session, Pix into the store's Pix
 *       account; a cancellation takes it out of the same account.
 * </ul>
 */
@Service
public class CounterSaleMoneyService {
  private final CashSessionRepository sessions;
  private final AccountRepository accounts;
  private final FinanceLedgerRepository ledger;
  private final Clock clock;

  public CounterSaleMoneyService(
      CashSessionRepository sessions,
      AccountRepository accounts,
      FinanceLedgerRepository ledger,
      Clock clock) {
    this.sessions = sessions;
    this.accounts = accounts;
    this.ledger = ledger;
    this.clock = clock;
  }

  public record OpenSession(UUID id, UUID accountId, String accountName) {}

  /** The store's open cash sessions, the ones a cash sale can go into. */
  @Transactional(readOnly = true)
  public List<OpenSession> openSessions(UUID tenant) {
    return sessions.openSessions(tenant).stream()
        .map(session -> new OpenSession(session.id(), session.accountId(), session.accountName()))
        .toList();
  }

  /** The drawer of an open session of the store, locking the session; empty otherwise. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Optional<UUID> lockOpenDrawer(UUID tenant, UUID session) {
    return sessions.lock(tenant, session).filter(CashSession::open).map(CashSession::accountId);
  }

  /**
   * A bank account of the store, with its name: the only kind that takes Pix. A drawer would count
   * money that is not in it at the blind close; Mercado Pago moves only with the provider.
   */
  @Transactional(readOnly = true)
  public Optional<String> bankAccountName(UUID tenant, UUID account) {
    return accounts
        .account(tenant, account)
        .filter(found -> "BANK".equals(found.type()))
        .map(found -> found.name());
  }

  /** A drawer or bank account of the store (SC-054: where an imported file's cash went). */
  @Transactional(readOnly = true)
  public Optional<String> cashOrBankAccountName(UUID tenant, UUID account) {
    return accounts
        .account(tenant, account)
        .filter(found -> "CASH".equals(found.type()) || "BANK".equals(found.type()))
        .map(found -> found.name());
  }

  /** What a sale paid: method, account and session (cash), fee (cards) and settlement. */
  public record Payment(
      UUID account, UUID session, BigDecimal fee, boolean settledNow, String description) {}

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recordSale(
      UUID tenant,
      UUID sale,
      BigDecimal total,
      BigDecimal knownCost,
      boolean costIncomplete,
      Payment payment,
      UUID actor) {
    recordSale(tenant, sale, total, knownCost, costIncomplete, payment, actor, now(), today());
  }

  /**
   * A sale that happened at {@code soldAt} (an imported one, SC-054): its facts count and settle
   * then, and its money moves on {@code soldOn}.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recordSale(
      UUID tenant,
      UUID sale,
      BigDecimal total,
      BigDecimal knownCost,
      boolean costIncomplete,
      Payment payment,
      UUID actor,
      Instant soldAt,
      LocalDate soldOn) {
    Instant now = now();
    Instant settled = payment.settledNow() ? soldAt : null;
    ledger.appendCounter(
        tenant, "counter-sale:" + sale, "SALE", total, soldAt, settled, sale, false, now);
    ledger.appendCounter(
        tenant,
        "counter-cogs:" + sale,
        "COGS",
        knownCost.negate(),
        soldAt,
        null,
        sale,
        costIncomplete,
        now);
    if (payment.fee() != null && payment.fee().signum() > 0)
      ledger.appendCounter(
          tenant,
          "counter-fee:" + sale,
          "FEE",
          payment.fee().negate(),
          soldAt,
          settled,
          sale,
          false,
          now);
    if (payment.account() != null)
      accounts.insertCounterMovement(
          tenant,
          payment.account(),
          "counter-sale:" + sale,
          "COUNTER_SALE",
          total,
          soldOn,
          payment.description(),
          sale,
          payment.session(),
          actor,
          now);
  }

  /**
   * A cancellation: REFUND of the total and, when the goods came back, the COGS reversal; cash and
   * Pix leave the account they came into (tied to the drawer's open session, if any). A card sale
   * still receivable keeps its refund receivable too, so the two cancel out.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recordRefund(
      UUID tenant,
      UUID sale,
      BigDecimal total,
      boolean reverseCost,
      UUID account,
      boolean settledNow,
      String description,
      UUID actor) {
    Instant now = now();
    Instant settled = settledNow ? now : null;
    ledger.appendCounter(
        tenant, "counter-refund:" + sale, "REFUND", total.negate(), now, settled, sale, false, now);
    if (reverseCost) {
      var cost =
          ledger
              .find(tenant, "counter-cogs:" + sale)
              .orElseThrow(() -> new IllegalStateException("Refund without a recorded sale"));
      ledger.appendCounter(
          tenant,
          "counter-cogs-reversal:" + sale,
          "COGS_REVERSAL",
          cost.amount().negate(),
          now,
          null,
          sale,
          cost.costIncomplete(),
          now);
    }
    if (account != null)
      accounts.insertCounterMovement(
          tenant,
          account,
          "counter-refund:" + sale,
          "COUNTER_SALE_REFUND",
          total.negate(),
          today(),
          description,
          sale,
          sessions.openFor(tenant, account).map(CashSession::id).orElse(null),
          actor,
          now);
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ExpenseService.ZONE));
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
