package com.smartcommerce.finance.services;

import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the ledger facts of orders (SC-019, ADR-0015 F1, F4, F7). Joins the caller's transaction,
 * the one that marks the order PAID or REFUNDED, so facts and states commit together. Keys are
 * derived from the order, so a repeated call records nothing new; the migration V22 uses the same
 * keys for orders paid before the ledger.
 *
 * <ul>
 *   <li>Sale: the order total (items and shipping) at {@code paid_at}.
 *   <li>COGS: the sum of the items' cost snapshots, flagged incomplete when an item had none.
 *   <li>Refund: minus the total at {@code refunded_at}; when the goods came back to stock, or never
 *       left it, the COGS is reversed at the same moment (otherwise it stays as a loss).
 * </ul>
 */
@Service
public class FinanceLedgerService {
  private final FinanceLedgerRepository ledger;
  private final Clock clock;

  public FinanceLedgerService(FinanceLedgerRepository ledger, Clock clock) {
    this.ledger = ledger;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recordSale(
      UUID tenant,
      UUID order,
      BigDecimal total,
      BigDecimal knownCost,
      boolean costIncomplete,
      Instant paidAt) {
    if (total.signum() <= 0 || knownCost.signum() < 0)
      throw new IllegalArgumentException("Invalid sale figures");
    Instant now = now();
    ledger.append(tenant, "sale:" + order, "SALE", total, paidAt, order, false, now);
    ledger.append(
        tenant, "cogs:" + order, "COGS", knownCost.negate(), paidAt, order, costIncomplete, now);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void recordRefund(
      UUID tenant, UUID order, BigDecimal total, boolean reverseCost, Instant at) {
    if (total.signum() <= 0) throw new IllegalArgumentException("Invalid refund figures");
    Instant now = now();
    ledger.append(tenant, "refund:" + order, "REFUND", total.negate(), at, order, false, now);
    if (!reverseCost) return;
    var cost =
        ledger
            .find(tenant, "cogs:" + order)
            .orElseThrow(() -> new IllegalStateException("Refund without a recorded sale"));
    ledger.append(
        tenant,
        "cogs-reversal:" + order,
        "COGS_REVERSAL",
        cost.amount().negate(),
        at,
        order,
        cost.costIncomplete(),
        now);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
