package com.smartcommerce.catalog.repositories;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * How a product appears in the storefront besides its price and description (Sprint 8, SC-041):
 * brand, address (slug, with former ones kept for redirects) and the "compare at" price of each
 * variant. Every statement is scoped by the tenant it is given; the caller has already checked the
 * OWNER through {@link CatalogRepository}.
 */
@Repository
public class ProductStorefrontRepository {
  private final JdbcTemplate jdbc;

  /** The locked product: its store and current address ({@code null} only for raw-SQL rows). */
  public record Locked(UUID store, String slug) {}

  public ProductStorefrontRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks the product row so two saves of the same card are applied one after the other. */
  public Optional<Locked> lock(UUID tenant, UUID product) {
    return jdbc
        .query(
            "SELECT store_id,slug FROM catalog_product WHERE tenant_id=? AND id=? FOR UPDATE",
            (row, index) ->
                new Locked(row.getObject("store_id", UUID.class), row.getString("slug")),
            tenant,
            product)
        .stream()
        .findFirst();
  }

  /** Current price of each variant of the product. */
  public Map<UUID, BigDecimal> variantPrices(UUID tenant, UUID product) {
    Map<UUID, BigDecimal> prices = new HashMap<>();
    jdbc.query(
        "SELECT id,price FROM catalog_variant WHERE tenant_id=? AND product_id=?",
        row -> {
          prices.put(row.getObject("id", UUID.class), row.getBigDecimal("price"));
        },
        tenant,
        product);
    return prices;
  }

  /** True when another product of the store already uses the address. */
  public boolean slugTaken(UUID tenant, String slug, UUID except) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog_product WHERE tenant_id=? AND slug=? AND id IS DISTINCT FROM ?",
            Integer.class,
            tenant,
            slug,
            except);
    return count != null && count > 0;
  }

  /**
   * Gives the product a new address and keeps the former one, so old links can be redirected. A
   * former address taken over by this product is no longer a redirect. Throws DuplicateKeyException
   * when another product took the address meanwhile.
   */
  public void changeSlug(
      UUID tenant, UUID store, UUID product, String former, String slug, Instant now) {
    jdbc.update(
        "UPDATE catalog_product SET slug=? WHERE tenant_id=? AND id=?", slug, tenant, product);
    jdbc.update(
        "DELETE FROM catalog_product_slug_history WHERE store_id=? AND slug=?", store, slug);
    if (former != null)
      jdbc.update(
          """
          INSERT INTO catalog_product_slug_history(store_id,slug,tenant_id,product_id,replaced_at)
          VALUES (?,?,?,?,?)
          ON CONFLICT (store_id,slug) DO UPDATE
          SET tenant_id=EXCLUDED.tenant_id,product_id=EXCLUDED.product_id,replaced_at=EXCLUDED.replaced_at
          """,
          store,
          former,
          tenant,
          product,
          Timestamp.from(now));
  }

  /**
   * Sets ({@code brand} present) or clears the product's brand. The composite foreign key refuses a
   * brand of another tenant (DataIntegrityViolationException).
   */
  public void assignBrand(UUID tenant, UUID product, UUID brand) {
    jdbc.update(
        "UPDATE catalog_product SET brand_id=? WHERE tenant_id=? AND id=?", brand, tenant, product);
  }

  public void compareAt(UUID tenant, UUID product, UUID variant, BigDecimal price) {
    jdbc.update(
        "UPDATE catalog_variant SET compare_at_price=? WHERE tenant_id=? AND product_id=? AND id=?",
        price,
        tenant,
        product,
        variant);
  }
}
