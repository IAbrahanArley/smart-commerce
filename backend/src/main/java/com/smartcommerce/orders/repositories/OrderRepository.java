package com.smartcommerce.orders.repositories;

import com.smartcommerce.orders.domain.CheckoutCommand;
import com.smartcommerce.orders.domain.CheckoutLine;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.domain.FulfillmentType;
import com.smartcommerce.orders.domain.ShippingAddress;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Orders persistence. Every statement is scoped by the tenant or store resolved by the server; the
 * public slug only selects among published stores. Callers hold one READ COMMITTED transaction.
 */
@Repository
public class OrderRepository {
  private static final String ORDER_COLUMNS =
      """
      o.id,o.tenant_id,o.number,o.status,o.currency,o.subtotal_amount,o.shipping_amount,
      o.total_amount,o.fulfillment_type,o.fulfillment_text,o.reservation_expires_at,o.created_at,
      o.request_fingerprint,o.access_token_hash
      """;
  private final JdbcTemplate jdbc;

  public OrderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<CheckoutRecords.Store> publishedStore(String slug) {
    return jdbc
        .query(
            "SELECT tenant_id,id FROM store WHERE slug=? AND status='PUBLISHED'",
            (row, index) ->
                new CheckoutRecords.Store(
                    row.getObject(1, UUID.class), row.getObject(2, UUID.class)),
            slug)
        .stream()
        .findFirst();
  }

  public CheckoutRecords.Delivery delivery(CheckoutRecords.Store store) {
    return jdbc
        .query(
            """
            SELECT CASE WHEN pickup_enabled THEN pickup_instructions END,
                   CASE WHEN fixed_shipping_enabled THEN fixed_shipping_amount END,
                   CASE WHEN fixed_shipping_enabled THEN fixed_shipping_area END
            FROM store_fulfillment WHERE store_id=? AND tenant_id=?
            """,
            (row, index) ->
                new CheckoutRecords.Delivery(
                    row.getString(1), row.getBigDecimal(2), row.getString(3)),
            store.storeId(),
            store.tenantId())
        .stream()
        .findFirst()
        .orElse(new CheckoutRecords.Delivery(null, null, null));
  }

  /** Only variants of published products of this published store; others are omitted. */
  public List<CheckoutRecords.Variant> variants(CheckoutRecords.Store store, Collection<UUID> ids) {
    return jdbc.query(
        """
        SELECT v.id,p.id,p.name,v.sku,v.price,v.cost,v.currency,v.options_json
        FROM catalog_variant v
        JOIN catalog_product p ON p.id=v.product_id AND p.tenant_id=v.tenant_id AND p.published
        JOIN store s ON s.id=p.store_id AND s.tenant_id=p.tenant_id AND s.status='PUBLISHED'
        WHERE s.id=? AND s.tenant_id=? AND v.id=ANY(CAST(? AS uuid[]))
        """,
        (row, index) ->
            new CheckoutRecords.Variant(
                row.getObject(1, UUID.class),
                row.getObject(2, UUID.class),
                row.getString(3),
                row.getString(4),
                row.getBigDecimal(5),
                row.getBigDecimal(6),
                row.getString(7),
                row.getString(8)),
        store.storeId(),
        store.tenantId(),
        array(ids));
  }

  /** Serializes attempts with the same request key; hash collisions only serialize, never grant. */
  public void lockRequest(UUID tenant, UUID request) {
    jdbc.queryForList(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 8013))",
        Object.class,
        "request:" + tenant + ":" + request);
  }

  /** Serializes checkouts of one buyer e-mail in one store, so the pending limit cannot race. */
  public void lockBuyer(UUID store, String emailKey) {
    jdbc.queryForList(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 8014))",
        Object.class,
        "buyer:" + store + ":" + emailKey);
  }

  public Optional<CheckoutRecords.Order> byRequest(UUID tenant, UUID request) {
    return jdbc
        .query(
            "SELECT "
                + ORDER_COLUMNS
                + " FROM sales_order o WHERE o.tenant_id=? AND o.request_id=?",
            OrderRepository::order,
            tenant,
            request)
        .stream()
        .findFirst();
  }

  public long pendingCount(UUID store, String emailKey, Instant now) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM sales_order
            WHERE store_id=? AND buyer_email_key=? AND status='PENDING_PAYMENT'
              AND reservation_expires_at > ?
            """,
            Long.class,
            store,
            emailKey,
            utc(now));
    return count == null ? Long.MAX_VALUE : count;
  }

  /** Allocates the next number of the store; the counter row stays locked until commit. */
  public long nextNumber(CheckoutRecords.Store store) {
    Long number =
        jdbc.queryForObject(
            """
            INSERT INTO sales_order_counter(store_id,tenant_id,last_number) VALUES (?,?,1)
            ON CONFLICT (store_id) DO UPDATE SET last_number=sales_order_counter.last_number+1
            RETURNING last_number
            """,
            Long.class,
            store.storeId(),
            store.tenantId());
    if (number == null) throw new IllegalStateException("Order number not allocated");
    return number;
  }

  public void insert(CheckoutRecords.NewOrder order) {
    CheckoutCommand command = order.command();
    ShippingAddress address = command.address();
    jdbc.update(
        """
        INSERT INTO sales_order(id,tenant_id,store_id,number,status,currency,subtotal_amount,
          shipping_amount,discount_amount,total_amount,fulfillment_type,fulfillment_text,
          buyer_name,buyer_email,buyer_email_key,buyer_phone,address_line1,address_line2,
          address_district,address_city,address_state,address_postal_code,
          reservation_expires_at,request_id,request_fingerprint,access_token_hash,
          created_at,updated_at)
        VALUES (?,?,?,?,'PENDING_PAYMENT','BRL',?,?,0,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        order.id(),
        order.store().tenantId(),
        order.store().storeId(),
        order.number(),
        order.subtotal(),
        order.shipping(),
        order.total(),
        command.fulfillment().name(),
        order.fulfillmentText(),
        command.buyer().name(),
        command.buyer().email(),
        command.buyer().emailKey(),
        command.buyer().phone(),
        address == null ? null : address.line1(),
        address == null ? null : address.line2(),
        address == null ? null : address.district(),
        address == null ? null : address.city(),
        address == null ? null : address.state(),
        address == null ? null : address.postalCode(),
        utc(order.reservationExpiresAt()),
        command.requestId(),
        command.fingerprint(),
        order.accessTokenHash(),
        utc(order.createdAt()),
        utc(order.createdAt()));
  }

  public void insertItems(
      UUID tenant,
      UUID order,
      List<CheckoutLine> lines,
      Map<UUID, CheckoutRecords.Variant> variants) {
    List<Object[]> rows = new ArrayList<>();
    for (int position = 0; position < lines.size(); position++) {
      CheckoutLine line = lines.get(position);
      CheckoutRecords.Variant variant = variants.get(line.variantId());
      rows.add(
          new Object[] {
            UUID.randomUUID(),
            tenant,
            order,
            variant.id(),
            variant.productId(),
            position,
            variant.productName(),
            variant.optionsJson(),
            variant.sku(),
            variant.price(),
            variant.cost(),
            line.quantity(),
            variant.price().multiply(BigDecimal.valueOf(line.quantity()))
          });
    }
    jdbc.batchUpdate(
        """
        INSERT INTO sales_order_item(id,tenant_id,order_id,variant_id,product_id,position,
          product_name_snapshot,variant_options_snapshot,sku_snapshot,unit_price_snapshot,
          unit_cost_snapshot,quantity,line_total)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        rows);
  }

  public void updateReservationExpiry(UUID tenant, UUID order, Instant expiresAt) {
    jdbc.update(
        "UPDATE sales_order SET reservation_expires_at=? WHERE tenant_id=? AND id=?",
        utc(expiresAt),
        tenant,
        order);
  }

  /**
   * Buyer read: the slug only selects the store (published or not, so a buyer keeps access to an
   * order after the store is hidden); the access token is verified by the caller.
   */
  public Optional<CheckoutRecords.Order> forBuyer(String slug, UUID order) {
    return jdbc
        .query(
            "SELECT "
                + ORDER_COLUMNS
                + " FROM sales_order o JOIN store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id"
                + " WHERE s.slug=? AND o.id=?",
            OrderRepository::order,
            slug,
            order)
        .stream()
        .findFirst();
  }

  /**
   * "Acompanhar pedido" (SC-046, ADR-0018 V10): the order of this number in the store of the slug,
   * only when the buyer e-mail key also matches. A wrong e-mail and a missing number are the same
   * empty result; an anonymized order (no e-mail kept) never matches.
   */
  public Optional<CheckoutRecords.Order> forLookup(String slug, long number, String emailKey) {
    return jdbc
        .query(
            "SELECT "
                + ORDER_COLUMNS
                + " FROM sales_order o JOIN store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id"
                + " WHERE s.slug=? AND o.number=? AND o.buyer_email_key=?",
            OrderRepository::order,
            slug,
            number,
            emailKey)
        .stream()
        .findFirst();
  }

  public List<CheckoutRecords.Item> items(UUID tenant, UUID order) {
    return jdbc.query(
        """
        SELECT product_name_snapshot,variant_options_snapshot,unit_price_snapshot,quantity,line_total
        FROM sales_order_item WHERE tenant_id=? AND order_id=? ORDER BY position
        """,
        (row, index) ->
            new CheckoutRecords.Item(
                row.getString(1),
                row.getString(2),
                row.getBigDecimal(3),
                row.getInt(4),
                row.getBigDecimal(5)),
        tenant,
        order);
  }

  private static CheckoutRecords.Order order(ResultSet row, int index) throws SQLException {
    return new CheckoutRecords.Order(
        row.getObject(1, UUID.class),
        row.getObject(2, UUID.class),
        row.getLong(3),
        row.getString(4),
        row.getString(5),
        row.getBigDecimal(6),
        row.getBigDecimal(7),
        row.getBigDecimal(8),
        FulfillmentType.valueOf(row.getString(9)),
        row.getString(10),
        row.getObject(11, OffsetDateTime.class).toInstant(),
        row.getObject(12, OffsetDateTime.class).toInstant(),
        row.getString(13),
        row.getString(14));
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static String array(Collection<UUID> ids) {
    var joiner = new StringJoiner(",", "{", "}");
    new TreeSet<>(ids).forEach(id -> joiner.add(id.toString()));
    return joiner.toString();
  }
}
