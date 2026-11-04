package com.smartcommerce.orders.repositories;

import com.smartcommerce.orders.domain.CounterSaleCursor;
import com.smartcommerce.orders.domain.CounterSaleInput;
import com.smartcommerce.orders.domain.CounterSaleRecords;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Counter sales (SC-053); every statement is scoped by the tenant it is given, after {@link
 * #lockMember} authorized the actor.
 */
@Repository
public class CounterSaleRepository {
  /** Variant options as "Cor: Azul · Tamanho: M" (empty for a simple product). */
  private static final String OPTIONS =
      """
      COALESCE((SELECT string_agg((o->>'name') || ': ' || (o->>'value'), ' · ')
        FROM jsonb_array_elements(v.options_json::jsonb) o), '')""";

  private final JdbcTemplate jdbc;

  public CounterSaleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The actor's role in the store (OWNER or STAFF) with an active, verified identity, locking the
   * store while the transaction lasts ({@code forUpdate} serializes the store's counter writes);
   * empty for anyone else.
   */
  public Optional<String> lockMember(UUID actor, UUID tenant, boolean forUpdate) {
    return jdbc
        .queryForList(
            """
            SELECT m.role FROM store s
            JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=?
              AND m.role IN ('OWNER','STAFF')
            JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            WHERE s.tenant_id=?
            """
                + (forUpdate ? " FOR UPDATE OF s FOR SHARE OF m, u" : " FOR SHARE OF s, m, u"),
            String.class,
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  /**
   * Variants whose product name or SKU contains {@code text}, published or not, at most {@code
   * limit}.
   */
  public List<CounterSaleRecords.Product> search(UUID tenant, String text, Instant now, int limit) {
    String like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return jdbc.query(
        PRODUCTS
            + " WHERE v.tenant_id=? AND (p.name ILIKE ? OR v.sku ILIKE ?)"
            + " ORDER BY p.name, v.sku LIMIT ?",
        CounterSaleRepository::product,
        utc(now),
        tenant,
        like,
        like,
        limit);
  }

  /** Variants by SKU (SC-054), with what is available now; unknown SKUs are omitted. */
  public List<CounterSaleRecords.Product> productsBySku(
      UUID tenant, Collection<String> skus, Instant now) {
    return jdbc.query(
        PRODUCTS + " WHERE v.tenant_id=? AND v.sku=ANY(CAST(? AS text[]))",
        CounterSaleRepository::product,
        utc(now),
        tenant,
        texts(skus));
  }

  /** The import keys already written in this store, among {@code keys}. */
  public Set<String> importedKeys(UUID tenant, Collection<String> keys) {
    if (keys.isEmpty()) return Set.of();
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT import_key FROM counter_sale WHERE tenant_id=?"
                + " AND import_key=ANY(CAST(? AS text[]))",
            String.class,
            tenant,
            texts(keys)));
  }

  public void insertImport(
      UUID tenant,
      UUID id,
      String fileHash,
      int itemLines,
      int salesCount,
      UUID cashAccount,
      UUID actor,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO counter_sale_import(id,tenant_id,file_hash,item_lines,sales_count,
          cash_account_id,created_by,created_at)
        VALUES (?,?,?,?,?,?,?,?)
        """,
        id,
        tenant,
        fileHash,
        itemLines,
        salesCount,
        cashAccount,
        actor,
        Timestamp.from(now));
  }

  /** The variants of a sale, for its snapshots; foreign or absent ones are omitted. */
  public List<CounterSaleRecords.Product> products(UUID tenant, Collection<UUID> ids, Instant now) {
    return jdbc.query(
        PRODUCTS + " WHERE v.tenant_id=? AND v.id=ANY(CAST(? AS uuid[]))",
        CounterSaleRepository::product,
        utc(now),
        tenant,
        array(ids));
  }

  private static final String PRODUCTS =
      "SELECT v.id, p.name, "
          + OPTIONS
          + """
           AS options, v.sku, v.price, v.cost,
            inventory_available_quantity(v.tenant_id, v.id, ?) AS available
          FROM catalog_variant v JOIN catalog_product p ON p.id=v.product_id AND p.tenant_id=v.tenant_id
          """;

  private static CounterSaleRecords.Product product(ResultSet row, int index) throws SQLException {
    BigDecimal cost = row.getBigDecimal("cost");
    return new CounterSaleRecords.Product(
        row.getObject("id", UUID.class),
        row.getString("name"),
        row.getString("options"),
        row.getString("sku"),
        row.getBigDecimal("price").setScale(2),
        cost == null ? null : cost.setScale(2),
        Math.max(0, row.getInt("available")));
  }

  public Optional<CounterSaleRecords.Settings> settings(UUID tenant) {
    return jdbc
        .query(
            """
            SELECT staff_discount_limit, pix_account_id, debit_fee_percent, debit_release_days,
              credit_fee_percent, credit_release_days FROM counter_sale_settings WHERE tenant_id=?
            """,
            (row, index) ->
                new CounterSaleRecords.Settings(
                    row.getBigDecimal(1).setScale(2),
                    row.getObject(2, UUID.class),
                    scaled(row.getBigDecimal(3)),
                    row.getObject(4, Integer.class),
                    scaled(row.getBigDecimal(5)),
                    row.getObject(6, Integer.class)),
            tenant)
        .stream()
        .findFirst();
  }

  public void saveSettings(
      UUID tenant, CounterSaleInput.Settings settings, UUID actor, Instant now) {
    jdbc.update(
        """
        INSERT INTO counter_sale_settings(tenant_id, staff_discount_limit, pix_account_id,
          debit_fee_percent, debit_release_days, credit_fee_percent, credit_release_days,
          updated_by, updated_at)
        VALUES (?,?,?,?,?,?,?,?,?)
        ON CONFLICT (tenant_id) DO UPDATE SET staff_discount_limit=EXCLUDED.staff_discount_limit,
          pix_account_id=EXCLUDED.pix_account_id, debit_fee_percent=EXCLUDED.debit_fee_percent,
          debit_release_days=EXCLUDED.debit_release_days,
          credit_fee_percent=EXCLUDED.credit_fee_percent,
          credit_release_days=EXCLUDED.credit_release_days, updated_by=EXCLUDED.updated_by,
          updated_at=EXCLUDED.updated_at
        """,
        tenant,
        settings.staffDiscountLimit(),
        settings.pixAccountId(),
        settings.debitFeePercent(),
        settings.debitReleaseDays(),
        settings.creditFeePercent(),
        settings.creditReleaseDays(),
        actor,
        Timestamp.from(now));
  }

  /** Inserts once per request id; returns whether this call created it. */
  public boolean insert(UUID tenant, CounterSaleRecords.Sale sale, UUID actor, Instant now) {
    return insert(tenant, sale, actor, now, null, null, null);
  }

  /**
   * Inserts once per request id; an imported sale also carries its import, its fingerprint and the
   * day it happened (SC-054). Returns whether this call created it.
   */
  public boolean insert(
      UUID tenant,
      CounterSaleRecords.Sale sale,
      UUID actor,
      Instant now,
      UUID importId,
      String importKey,
      LocalDate soldOn) {
    boolean created =
        jdbc.update(
                """
                INSERT INTO counter_sale(id,tenant_id,request_id,payment_method,subtotal_amount,
                  discount_amount,total_amount,fee_percent,fee_amount,account_id,cash_session_id,
                  expected_release_on,status,created_by,created_at,import_id,import_key,sold_on)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,?)
                ON CONFLICT (tenant_id, request_id) DO NOTHING
                """,
                sale.id(),
                tenant,
                sale.requestId(),
                sale.paymentMethod(),
                sale.subtotal(),
                sale.discount(),
                sale.total(),
                sale.feePercent(),
                sale.fee(),
                sale.accountId(),
                sale.cashSessionId(),
                sale.expectedReleaseOn() == null ? null : Date.valueOf(sale.expectedReleaseOn()),
                actor,
                Timestamp.from(now),
                importId,
                importKey,
                soldOn == null ? null : Date.valueOf(soldOn))
            == 1;
    if (!created) return false;
    int position = 0;
    for (var item : sale.items())
      jdbc.update(
          """
          INSERT INTO counter_sale_item(id,tenant_id,counter_sale_id,variant_id,position,
            product_name_snapshot,variant_options_snapshot,sku_snapshot,unit_price_snapshot,
            unit_cost_snapshot,quantity,line_total)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
          """,
          UUID.randomUUID(),
          tenant,
          sale.id(),
          item.variantId(),
          position++,
          item.name(),
          item.options(),
          item.sku(),
          item.unitPrice(),
          item.unitCost(),
          item.quantity(),
          item.lineTotal());
    return true;
  }

  public boolean cancel(
      UUID tenant, UUID id, UUID actor, String reason, boolean restocked, Instant now) {
    return jdbc.update(
            """
            UPDATE counter_sale SET status='CANCELLED', cancelled_by=?, cancelled_at=?,
              cancel_reason=?, restocked=?
            WHERE tenant_id=? AND id=? AND status='ACTIVE'
            """,
            actor,
            Timestamp.from(now),
            reason,
            restocked,
            tenant,
            id)
        == 1;
  }

  public Optional<CounterSaleRecords.Sale> byRequest(UUID tenant, UUID request) {
    return one(tenant, " WHERE c.tenant_id=? AND c.request_id=?", request);
  }

  public Optional<CounterSaleRecords.Sale> byId(UUID tenant, UUID id) {
    return one(tenant, " WHERE c.tenant_id=? AND c.id=?", id);
  }

  public Optional<CounterSaleRecords.Sale> lock(UUID tenant, UUID id) {
    return one(tenant, " WHERE c.tenant_id=? AND c.id=? FOR UPDATE OF c", id);
  }

  /**
   * The store's sales, newest first, after {@code cursor} (the last sale of the previous page) when
   * given, at most {@code limit}, with their items.
   */
  public List<CounterSaleRecords.Sale> page(UUID tenant, CounterSaleCursor cursor, int limit) {
    String where =
        cursor == null
            ? " WHERE c.tenant_id=?"
            : " WHERE c.tenant_id=? AND (c.created_at,c.id)<(?,?)";
    Object[] args =
        cursor == null
            ? new Object[] {tenant, limit}
            : new Object[] {tenant, utc(cursor.createdAt()), cursor.id(), limit};
    return jdbc
        .query(
            SALE + where + " ORDER BY c.created_at DESC, c.id DESC LIMIT ?",
            CounterSaleRepository::sale,
            args)
        .stream()
        .map(sale -> withItems(tenant, sale))
        .toList();
  }

  private Optional<CounterSaleRecords.Sale> one(UUID tenant, String where, UUID key) {
    return jdbc.query(SALE + where, CounterSaleRepository::sale, tenant, key).stream()
        .findFirst()
        .map(sale -> withItems(tenant, sale));
  }

  private CounterSaleRecords.Sale withItems(UUID tenant, CounterSaleRecords.Sale sale) {
    var items =
        jdbc.query(
            """
            SELECT variant_id, product_name_snapshot, variant_options_snapshot, sku_snapshot,
              unit_price_snapshot, unit_cost_snapshot, quantity, line_total
            FROM counter_sale_item WHERE tenant_id=? AND counter_sale_id=? ORDER BY position
            """,
            (row, index) ->
                new CounterSaleRecords.Item(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getString(3),
                    row.getString(4),
                    row.getBigDecimal(5).setScale(2),
                    scaled(row.getBigDecimal(6)),
                    row.getInt(7),
                    row.getBigDecimal(8).setScale(2)),
            tenant,
            sale.id());
    return new CounterSaleRecords.Sale(
        sale.id(),
        sale.requestId(),
        sale.paymentMethod(),
        sale.subtotal(),
        sale.discount(),
        sale.total(),
        sale.feePercent(),
        sale.fee(),
        sale.accountId(),
        sale.accountName(),
        sale.cashSessionId(),
        sale.expectedReleaseOn(),
        sale.status(),
        sale.createdBy(),
        sale.createdAt(),
        sale.cancelledBy(),
        sale.cancelledAt(),
        sale.cancelReason(),
        sale.restocked(),
        items,
        sale.soldOn());
  }

  private static final String SALE =
      """
      SELECT c.id, c.request_id, c.payment_method, c.subtotal_amount, c.discount_amount,
        c.total_amount, c.fee_percent, c.fee_amount, c.account_id, a.name AS account_name,
        c.cash_session_id, c.expected_release_on, c.status, o.email_original AS created_by,
        c.created_at, k.email_original AS cancelled_by, c.cancelled_at, c.cancel_reason,
        c.restocked, c.sold_on
      FROM counter_sale c
      JOIN identity_user o ON o.id=c.created_by
      LEFT JOIN identity_user k ON k.id=c.cancelled_by
      LEFT JOIN finance_account a ON a.id=c.account_id AND a.tenant_id=c.tenant_id""";

  private static CounterSaleRecords.Sale sale(ResultSet row, int index) throws SQLException {
    Date release = row.getDate("expected_release_on");
    Date soldOn = row.getDate("sold_on");
    return new CounterSaleRecords.Sale(
        row.getObject("id", UUID.class),
        row.getObject("request_id", UUID.class),
        row.getString("payment_method"),
        row.getBigDecimal("subtotal_amount").setScale(2),
        row.getBigDecimal("discount_amount").setScale(2),
        row.getBigDecimal("total_amount").setScale(2),
        scaled(row.getBigDecimal("fee_percent")),
        scaled(row.getBigDecimal("fee_amount")),
        row.getObject("account_id", UUID.class),
        row.getString("account_name"),
        row.getObject("cash_session_id", UUID.class),
        release == null ? null : release.toLocalDate(),
        row.getString("status"),
        row.getString("created_by"),
        instant(row, "created_at"),
        row.getString("cancelled_by"),
        instant(row, "cancelled_at"),
        row.getString("cancel_reason"),
        row.getObject("restocked", Boolean.class),
        List.of(),
        soldOn == null ? null : soldOn.toLocalDate());
  }

  private static BigDecimal scaled(BigDecimal value) {
    return value == null ? null : value.setScale(2);
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  /** A text[] literal; SKUs and hex keys never hold quotes, commas or braces. */
  private static String texts(Collection<String> values) {
    var joiner = new StringJoiner(",", "{", "}");
    new TreeSet<>(values).forEach(value -> joiner.add("\"" + value + "\""));
    return joiner.toString();
  }

  private static String array(Collection<UUID> ids) {
    var joiner = new StringJoiner(",", "{", "}");
    new TreeSet<>(ids).forEach(id -> joiner.add(id.toString()));
    return joiner.toString();
  }
}
