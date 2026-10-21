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
