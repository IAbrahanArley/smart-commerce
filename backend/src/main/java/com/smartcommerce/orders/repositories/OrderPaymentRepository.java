package com.smartcommerce.orders.repositories;

import com.smartcommerce.inventory.domain.ReservationLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Payment confirmation writes on orders (SC-014); always scoped by the payment's own tenant. */
@Repository
public class OrderPaymentRepository {
  private final JdbcTemplate jdbc;

  public OrderPaymentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The order row, locked: number, state and whether its personal data was already removed. */
  public Optional<Locked> lock(UUID tenant, UUID order) {
    return jdbc
        .query(
            """
            SELECT number,status,anonymized_at IS NOT NULL,paid_without_stock FROM sales_order
            WHERE tenant_id=? AND id=? FOR UPDATE
            """,
            (row, index) ->
                new Locked(row.getLong(1), row.getString(2), row.getBoolean(3), row.getBoolean(4)),
            tenant,
            order)
        .stream()
        .findFirst();
  }

  /** What each item takes out of stock. */
  public List<ReservationLine> lines(UUID tenant, UUID order) {
    return jdbc.query(
        "SELECT variant_id,quantity FROM sales_order_item WHERE tenant_id=? AND order_id=?",
        (row, index) -> new ReservationLine(row.getObject(1, UUID.class), row.getInt(2)),
        tenant,
        order);
  }

  /** PENDING_PAYMENT, EXPIRED or CANCELLED to PAID once (P4, P8). */
  public boolean markPaid(UUID tenant, UUID order, boolean withoutStock, Instant now) {
    return jdbc.update(
            """
            UPDATE sales_order SET status='PAID', paid_at=?, paid_without_stock=?, updated_at=?,
              version=version+1
            WHERE tenant_id=? AND id=? AND anonymized_at IS NULL
              AND status IN ('PENDING_PAYMENT','EXPIRED','CANCELLED')
            """,
            utc(now),
            withoutStock,
            utc(now),
            tenant,
            order)
        == 1;
  }

  /**
   * The order's figures for the finance ledger (SC-019): total, known cost of its items and whether
   * any item had no cost snapshot (ADR-0015 F4).
   */
  public SaleFigures saleFigures(UUID tenant, UUID order) {
    return jdbc.queryForObject(
        """
        SELECT o.total_amount,
          COALESCE(SUM(i.unit_cost_snapshot * i.quantity),0),
          COALESCE(bool_or(i.unit_cost_snapshot IS NULL),false)
        FROM sales_order o JOIN sales_order_item i ON i.order_id=o.id AND i.tenant_id=o.tenant_id
        WHERE o.tenant_id=? AND o.id=? GROUP BY o.total_amount
        """,
        (row, index) ->
            new SaleFigures(row.getBigDecimal(1), row.getBigDecimal(2), row.getBoolean(3)),
        tenant,
        order);
  }

  public record SaleFigures(BigDecimal total, BigDecimal knownCost, boolean costIncomplete) {}

  /** PAID to REFUNDED once (SC-016). */
  public boolean markRefunded(UUID tenant, UUID order, Instant now) {
    return jdbc.update(
            """
            UPDATE sales_order SET status='REFUNDED', refunded_at=?, updated_at=?,
              version=version+1
            WHERE tenant_id=? AND id=? AND status='PAID'
            """,
            utc(now),
            utc(now),
            tenant,
            order)
        == 1;
  }

  public record Locked(long number, String status, boolean anonymized, boolean paidWithoutStock) {}

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
