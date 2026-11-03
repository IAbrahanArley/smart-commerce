package com.smartcommerce.finance.repositories;

import com.smartcommerce.finance.domain.FinanceEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Append-only finance ledger (SC-019, ADR-0015 F7). Rows are never updated or deleted; every
 * statement is scoped by the tenant it is given.
 */
@Repository
public class FinanceLedgerRepository {
  private final JdbcTemplate jdbc;

  public FinanceLedgerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Records a fact once: a second append with the same key changes nothing and returns false. */
  public boolean append(
      UUID tenant,
      String key,
      String type,
      BigDecimal amount,
      Instant competenceAt,
      UUID order,
      boolean costIncomplete,
      Instant now) {
    return jdbc.update(
            """
            INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,
              order_id,cost_incomplete,created_at)
            VALUES (?,?,?,?,?,'BRL',?,?,?,?)
            ON CONFLICT (tenant_id, entry_key) DO NOTHING
            """,
            UUID.randomUUID(),
            tenant,
            key,
            type,
            amount,
            utc(competenceAt),
            order,
            costIncomplete,
            utc(now))
        == 1;
  }

  /**
   * Records an expense fact once (SC-020): EXPENSE (negative) or its EXPENSE_REVERSAL (positive),
   * with the expense's competence and, when paid, its settlement.
   */
  public boolean appendExpense(
      UUID tenant,
      String key,
      String type,
      BigDecimal amount,
      Instant competenceAt,
      Instant settledAt,
      UUID expense,
      Instant now) {
    return jdbc.update(
            """
            INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,
              settled_at,expense_id,created_at)
            VALUES (?,?,?,?,?,'BRL',?,?,?,?)
            ON CONFLICT (tenant_id, entry_key) DO NOTHING
            """,
            UUID.randomUUID(),
            tenant,
            key,
            type,
            amount,
            utc(competenceAt),
            settledAt == null ? null : utc(settledAt),
            expense,
            utc(now))
        == 1;
  }

  /**
   * Fills the settlement of one entry once, when its money moves (SC-051: a payable is paid). The
   * database allows nothing else on the ledger; returns whether this call filled it.
   */
  public boolean settle(UUID tenant, String key, Instant settledAt) {
    return jdbc.update(
            "UPDATE finance_entry SET settled_at=? WHERE tenant_id=? AND entry_key=?"
                + " AND settled_at IS NULL",
            utc(settledAt),
            tenant,
            key)
        == 1;
  }

  /**
   * Records a cash difference once (SC-052): counted minus expected, either sign, as its own result
   * line, with competence and settlement on the day it was found.
   */
  public boolean appendCashDifference(
      UUID tenant, String key, BigDecimal amount, Instant day, UUID session, Instant now) {
    return jdbc.update(
            """
            INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,
              settled_at,cash_session_id,created_at)
            VALUES (?,?,?,'CASH_DIFFERENCE',?,'BRL',?,?,?,?)
            ON CONFLICT (tenant_id, entry_key) DO NOTHING
            """,
            UUID.randomUUID(),
            tenant,
            key,
            amount,
            utc(day),
            utc(day),
            session,
            utc(now))
        == 1;
  }

  public Optional<FinanceEntry> find(UUID tenant, String key) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND entry_key=?",
            FinanceLedgerRepository::entry,
            tenant,
            key)
        .stream()
        .findFirst();
  }

  /** Entries of one order, oldest first. */
  public List<FinanceEntry> forOrder(UUID tenant, UUID order) {
    return jdbc.query(
        SELECT + " WHERE tenant_id=? AND order_id=? ORDER BY competence_at, entry_key",
        FinanceLedgerRepository::entry,
        tenant,
        order);
  }

  private static final String SELECT =
      """
      SELECT entry_key,type,amount,competence_at,settled_at,order_id,cost_incomplete
      FROM finance_entry""";

  private static FinanceEntry entry(java.sql.ResultSet row, int index)
      throws java.sql.SQLException {
    OffsetDateTime settled = row.getObject("settled_at", OffsetDateTime.class);
    return new FinanceEntry(
        row.getString("entry_key"),
        row.getString("type"),
        row.getBigDecimal("amount"),
        row.getObject("competence_at", OffsetDateTime.class).toInstant(),
        settled == null ? null : settled.toInstant(),
        row.getObject("order_id", UUID.class),
        row.getBoolean("cost_incomplete"));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
