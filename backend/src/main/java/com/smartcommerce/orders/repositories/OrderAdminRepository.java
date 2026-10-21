package com.smartcommerce.orders.repositories;

import com.smartcommerce.orders.domain.AdminOrderQuery;
import com.smartcommerce.orders.domain.AdminOrderRecords;
import com.smartcommerce.orders.domain.FulfillmentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Store OWNER reads and state changes (SC-010). Every statement joins the actor's OWNER membership
 * and an active, verified identity, so a foreign tenant or a lost membership reads as absent.
 */
@Repository
public class OrderAdminRepository {
  private static final String OWNER =
      """
      JOIN tenant_membership m ON m.tenant_id=o.tenant_id AND m.user_id=? AND m.role='OWNER'
      JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
      """;
  private static final String EFFECTIVE_STATUS =
      "CASE WHEN o.status='PENDING_PAYMENT' AND o.reservation_expires_at<=? THEN 'EXPIRED'"
          + " ELSE o.status END";
  private final JdbcTemplate jdbc;

  public OrderAdminRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean ownsStore(UUID actor, UUID tenant) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS(SELECT 1 FROM store s
              JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=? AND m.role='OWNER'
              JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
              WHERE s.tenant_id=?)
            """,
            Boolean.class,
            actor,
            tenant));
  }

  /** One page plus one extra row, so the caller knows whether another page exists. */
  public List<AdminOrderRecords.Summary> list(
      UUID actor, UUID tenant, AdminOrderQuery query, Instant now) {
    List<Object> args = new ArrayList<>(List.of(utc(now), actor, tenant));
    StringBuilder sql =
        new StringBuilder(
            "SELECT o.id,o.number,"
                + EFFECTIVE_STATUS
                + ",o.total_amount,o.buyer_name,o.fulfillment_type,"
                + "(SELECT COALESCE(sum(i.quantity),0) FROM sales_order_item i"
                + " WHERE i.tenant_id=o.tenant_id AND i.order_id=o.id),"
                + "o.created_at,o.reservation_expires_at FROM sales_order o "
                + OWNER
                + " WHERE o.tenant_id=?");
    if (query.status() != null) {
      switch (query.status()) {
        case "PENDING_PAYMENT" ->
            sql.append(" AND o.status='PENDING_PAYMENT' AND o.reservation_expires_at>?");
        case "EXPIRED" ->
            sql.append(
                " AND (o.status='EXPIRED' OR (o.status='PENDING_PAYMENT'"
                    + " AND o.reservation_expires_at<=?))");
        case "PAID" -> sql.append(" AND o.status='PAID'");
        case "REFUNDED" -> sql.append(" AND o.status='REFUNDED'");
        default -> sql.append(" AND o.status='CANCELLED'");
      }
      if ("PENDING_PAYMENT".equals(query.status()) || "EXPIRED".equals(query.status()))
        args.add(utc(now));
    }
    if (query.after() != null) {
      sql.append(" AND o.number<?");
      args.add(query.after());
    }
    sql.append(" ORDER BY o.number DESC LIMIT ?");
    args.add(AdminOrderQuery.PAGE_SIZE + 1);
    return jdbc.query(
        sql.toString(),
        (row, index) ->
            new AdminOrderRecords.Summary(
                row.getObject(1, UUID.class),
                row.getLong(2),
                row.getString(3),
                row.getBigDecimal(4),
                row.getString(5),
                FulfillmentType.valueOf(row.getString(6)),
                row.getInt(7),
                instant(row, 8),
                instant(row, 9)),
        args.toArray());
  }

  public Optional<AdminOrderRecords.Detail> detail(
      UUID actor, UUID tenant, UUID order, Instant now) {
    return jdbc
        .query(
            "SELECT o.id,o.number,"
                + EFFECTIVE_STATUS
                + """
                ,o.subtotal_amount,o.shipping_amount,o.total_amount,o.fulfillment_type,
                o.fulfillment_text,o.buyer_name,o.buyer_email,o.buyer_phone,o.address_line1,
                o.address_line2,o.address_district,o.address_city,o.address_state,
                o.address_postal_code,o.reservation_expires_at,o.created_at,o.expired_at,
                o.cancelled_at,o.cancel_reason,o.anonymized_at,o.paid_at,o.paid_without_stock,o.refunded_at
                FROM sales_order o
                """
                + OWNER
                + " WHERE o.tenant_id=? AND o.id=?",
            OrderAdminRepository::detail,
            utc(now),
            actor,
            tenant,
            order)
        .stream()
        .findFirst();
  }

  public List<AdminOrderRecords.Item> items(UUID tenant, UUID order) {
    return jdbc.query(
        """
        SELECT product_name_snapshot,variant_options_snapshot,sku_snapshot,unit_price_snapshot,
          unit_cost_snapshot,quantity,line_total
        FROM sales_order_item WHERE tenant_id=? AND order_id=? ORDER BY position
        """,
        (row, index) ->
            new AdminOrderRecords.Item(
                row.getString(1),
                row.getString(2),
                row.getString(3),
                row.getBigDecimal(4),
                row.getBigDecimal(5),
                row.getInt(6),
                row.getBigDecimal(7)),
        tenant,
        order);
  }

  /**
   * Locks the order row for a state change; the membership and identity rows are share-locked so
   * authorization holds until commit. Lock order everywhere: order row, then stock rows.
   */
  public Optional<AdminOrderRecords.Owned> lockOwned(UUID actor, UUID tenant, UUID order) {
    return jdbc
        .query(
            "SELECT o.id,o.tenant_id,o.status,o.reservation_expires_at FROM sales_order o "
                + OWNER
                + " WHERE o.tenant_id=? AND o.id=? FOR UPDATE OF o FOR SHARE OF m,u",
            (row, index) ->
                new AdminOrderRecords.Owned(
                    row.getObject(1, UUID.class),
                    row.getObject(2, UUID.class),
                    row.getString(3),
                    instant(row, 4)),
            actor,
            tenant,
            order)
        .stream()
        .findFirst();
  }

  /** Compare-and-set: only a pending order still inside its deadline is cancelled. */
  public boolean cancel(UUID tenant, UUID order, UUID actor, String reason, Instant now) {
    return jdbc.update(
            """
            UPDATE sales_order SET status='CANCELLED',cancelled_at=?,cancelled_by=?,cancel_reason=?,
              updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND status='PENDING_PAYMENT' AND reservation_expires_at>?
            """,
            utc(now),
            actor,
            reason,
            utc(now),
            tenant,
            order,
            utc(now))
        == 1;
  }

  private static AdminOrderRecords.Detail detail(ResultSet row, int index) throws SQLException {
    return new AdminOrderRecords.Detail(
        row.getObject(1, UUID.class),
        row.getLong(2),
        row.getString(3),
        row.getBigDecimal(4),
        row.getBigDecimal(5),
        row.getBigDecimal(6),
        FulfillmentType.valueOf(row.getString(7)),
        row.getString(8),
        row.getString(9),
        row.getString(10),
        row.getString(11),
        row.getString(12),
        row.getString(13),
        row.getString(14),
        row.getString(15),
        row.getString(16),
        row.getString(17),
        instant(row, 18),
        instant(row, 19),
        instant(row, 20),
        instant(row, 21),
        row.getString(22),
        instant(row, 23),
        instant(row, 24),
        row.getBoolean(25),
        instant(row, 26));
  }

  private static Instant instant(ResultSet row, int column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
