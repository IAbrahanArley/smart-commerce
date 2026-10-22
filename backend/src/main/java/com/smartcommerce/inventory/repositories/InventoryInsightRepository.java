package com.smartcommerce.inventory.repositories;

import com.smartcommerce.inventory.domain.StockInsight;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stock read models for the dashboard (SC-022, ADR-0015 F8). Availability uses the single V11
 * formula (physical − ACTIVE reservations in force); every query is scoped by the tenant.
 */
@Repository
public class InventoryInsightRepository {
  private final JdbcTemplate jdbc;

  public InventoryInsightRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Variants with at most {@code threshold} units available, lowest first; {@code total} is all.
   */
  public List<StockInsight> lowStock(UUID tenant, Instant now, int threshold, int limit) {
    return jdbc.query(
        """
        SELECT * FROM (
          SELECT v.id AS variant_id, p.id AS product_id, p.name, v.options_json,
            inventory_available_quantity(v.tenant_id, v.id, ?) AS available, NULL::timestamptz AS last_sale
          FROM catalog_variant v
          JOIN catalog_product p ON p.id=v.product_id AND p.tenant_id=v.tenant_id
          WHERE v.tenant_id=?) s
        WHERE s.available <= ?
        ORDER BY s.available, s.name, s.variant_id LIMIT ?
        """,
        InventoryInsightRepository::insight,
        InventoryReservationRepository.utc(now),
        tenant,
        threshold,
        limit);
  }

  public long lowStockCount(UUID tenant, Instant now, int threshold) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM catalog_variant v
            WHERE v.tenant_id=? AND inventory_available_quantity(v.tenant_id, v.id, ?) <= ?
            """,
            Long.class,
            tenant,
            InventoryReservationRepository.utc(now),
            threshold);
    return count == null ? 0 : count;
  }

  /**
   * Variants with stock but no sale since {@code since}, of products created before it (a new
   * product is not "without turnover" yet). Oldest last sale first, never sold at the top.
   */
  public List<StockInsight> withoutSales(UUID tenant, Instant now, Instant since, int limit) {
    return jdbc.query(
        STALE + " ORDER BY s.last_sale NULLS FIRST, s.name, s.variant_id LIMIT ?",
        InventoryInsightRepository::insight,
        InventoryReservationRepository.utc(now),
        tenant,
        InventoryReservationRepository.utc(since),
        InventoryReservationRepository.utc(since),
        limit);
  }

  public long withoutSalesCount(UUID tenant, Instant now, Instant since) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM (" + STALE + ") c",
            Long.class,
            InventoryReservationRepository.utc(now),
            tenant,
            InventoryReservationRepository.utc(since),
            InventoryReservationRepository.utc(since));
    return count == null ? 0 : count;
  }

  private static final String STALE =
      """
      SELECT * FROM (
        SELECT v.id AS variant_id, p.id AS product_id, p.name, v.options_json,
          inventory_available_quantity(v.tenant_id, v.id, ?) AS available,
          (SELECT max(m.created_at) FROM inventory_movement m
            WHERE m.tenant_id=v.tenant_id AND m.product_id=v.id AND m.type='SALE') AS last_sale
        FROM catalog_variant v
        JOIN catalog_product p ON p.id=v.product_id AND p.tenant_id=v.tenant_id
        WHERE v.tenant_id=? AND p.created_at < ?) s
      WHERE s.available > 0 AND (s.last_sale IS NULL OR s.last_sale < ?)""";

  private static StockInsight insight(ResultSet row, int index) throws SQLException {
    var lastSale = row.getObject("last_sale", java.time.OffsetDateTime.class);
    return new StockInsight(
        row.getObject("variant_id", UUID.class),
        row.getObject("product_id", UUID.class),
        row.getString("name"),
        row.getString("options_json"),
        row.getInt("available"),
        lastSale == null ? null : lastSale.toInstant());
  }
}
