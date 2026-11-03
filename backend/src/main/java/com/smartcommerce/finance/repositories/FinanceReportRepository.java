package com.smartcommerce.finance.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregates of the finance ledger (SC-021, ADR-0015 F3, F5). Reads only {@code finance_entry}
 * (plus the expense kind), always scoped by the tenant it is given.
 */
@Repository
public class FinanceReportRepository {
  private final JdbcTemplate jdbc;

  public FinanceReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Signed sums by competence in [from, to): sales, refunds, fees, COGS (with reversals), variable
   * and fixed expenses (with reversals), number of sales, sales without a fee entry, COGS entries
   * built from incomplete costs and cash differences (SC-052, signed: negative is cash missing).
   */
  public Totals totals(UUID tenant, Instant from, Instant to) {
    return jdbc.queryForObject(
        """
        SELECT
          COALESCE(SUM(e.amount) FILTER (WHERE e.type='SALE'),0),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type='REFUND'),0),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type='FEE'),0),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type IN ('COGS','COGS_REVERSAL')),0),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type IN ('EXPENSE','EXPENSE_REVERSAL')
            AND x.kind='VARIABLE'),0),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type IN ('EXPENSE','EXPENSE_REVERSAL')
            AND x.kind='FIXED'),0),
          COUNT(*) FILTER (WHERE e.type='SALE'),
          COUNT(*) FILTER (WHERE e.type='SALE' AND ((e.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM finance_entry f
              WHERE f.tenant_id=e.tenant_id AND f.order_id=e.order_id AND f.type='FEE'))
            OR (e.counter_sale_id IS NOT NULL AND EXISTS (SELECT 1 FROM counter_sale c
              WHERE c.id=e.counter_sale_id AND c.tenant_id=e.tenant_id
                AND c.payment_method IN ('DEBIT','CREDIT') AND c.fee_amount IS NULL)))),
          COUNT(*) FILTER (WHERE e.type='COGS' AND e.cost_incomplete),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type='CASH_DIFFERENCE'),0),
          COALESCE(SUM(e.amount) FILTER (WHERE e.type='FEE' AND e.counter_sale_id IS NOT NULL),0)
        FROM finance_entry e
        LEFT JOIN expense x ON x.id=e.expense_id AND x.tenant_id=e.tenant_id
        WHERE e.tenant_id=? AND e.competence_at>=? AND e.competence_at<?
        """,
        (row, index) ->
            new Totals(
                row.getBigDecimal(1),
                row.getBigDecimal(2),
                row.getBigDecimal(3),
                row.getBigDecimal(4),
                row.getBigDecimal(5),
                row.getBigDecimal(6),
                row.getLong(7),
                row.getLong(8),
                row.getLong(9),
                row.getBigDecimal(10),
                row.getBigDecimal(11)),
        tenant,
        utc(from),
        utc(to));
  }

  /** Sales per São Paulo calendar day in [from, to) (SC-022): day, count and gross revenue. */
  public List<DaySales> salesByDay(UUID tenant, Instant from, Instant to) {
    return jdbc.query(
        """
        SELECT (competence_at AT TIME ZONE 'America/Sao_Paulo')::date, count(*), sum(amount)
        FROM finance_entry
        WHERE tenant_id=? AND type='SALE' AND competence_at>=? AND competence_at<?
        GROUP BY 1 ORDER BY 1
        """,
        (row, index) ->
            new DaySales(row.getDate(1).toLocalDate(), row.getLong(2), row.getBigDecimal(3)),
        tenant,
        utc(from),
        utc(to));
  }

  public record DaySales(LocalDate day, long orders, BigDecimal revenue) {}

  /** Money that moved in [from, to) by settlement date (F3): expenses paid, releases (SC-018). */
  public BigDecimal settled(UUID tenant, Instant from, Instant to) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(amount),0) FROM finance_entry
        WHERE tenant_id=? AND settled_at>=? AND settled_at<?
        """,
        BigDecimal.class,
        tenant,
        utc(from),
        utc(to));
  }

  /**
   * Sales still to be received until {@code until}, by competence: sales, refunds and fees not yet
   * settled (F3). Without a release date from the provider this is "a receber, data desconhecida".
   */
  public Receivable receivable(UUID tenant, Instant until) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(e.amount),0),
          COUNT(DISTINCT COALESCE(e.order_id, e.counter_sale_id)) FILTER (WHERE e.type='SALE'
            AND ((e.order_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM finance_entry f
              WHERE f.tenant_id=e.tenant_id AND f.order_id=e.order_id AND f.type='FEE'))
            OR (e.counter_sale_id IS NOT NULL AND EXISTS (SELECT 1 FROM counter_sale c
              WHERE c.id=e.counter_sale_id AND c.tenant_id=e.tenant_id
                AND c.payment_method IN ('DEBIT','CREDIT') AND c.fee_amount IS NULL))))
        FROM finance_entry e
        WHERE e.tenant_id=? AND e.type IN ('SALE','REFUND','FEE') AND e.settled_at IS NULL
          AND e.competence_at<?
        """,
        (row, index) -> new Receivable(row.getBigDecimal(1), row.getLong(2)),
        tenant,
        utc(until));
  }

  public record Totals(
      BigDecimal sales,
      BigDecimal refunds,
      BigDecimal fees,
      BigDecimal cogs,
      BigDecimal variableExpenses,
      BigDecimal fixedExpenses,
      long salesCount,
      long salesWithoutFee,
      long incompleteCosts,
      BigDecimal cashDifferences,
      BigDecimal informedFees) {}

  public record Receivable(BigDecimal amount, long salesWithoutFee) {}

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
