package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.BrandSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Brands of a store (Sprint 8, SC-041). Every statement is scoped by the tenant it is given; the
 * caller has already checked the OWNER (and locked the store) through {@link CatalogRepository}.
 */
@Repository
public class BrandRepository {
  private final JdbcTemplate jdbc;

  public BrandRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Brands with how many products have each, by name. */
  public List<BrandSummary> list(UUID tenant) {
    return jdbc.query(
        """
        SELECT b.id,b.name,b.slug,count(p.id) AS products
        FROM catalog_brand b
        LEFT JOIN catalog_product p ON p.brand_id=b.id AND p.tenant_id=b.tenant_id
        WHERE b.tenant_id=?
        GROUP BY b.id
        ORDER BY b.name_key
        """,
        (row, index) ->
            new BrandSummary(
                row.getObject("id", UUID.class),
                row.getString("name"),
                row.getString("slug"),
                row.getLong("products")),
        tenant);
  }

  public int count(UUID tenant) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog_brand WHERE tenant_id=?", Integer.class, tenant);
    return count == null ? 0 : count;
  }

  public boolean slugTaken(UUID tenant, String slug) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog_brand WHERE tenant_id=? AND slug=?",
            Integer.class,
            tenant,
            slug);
    return count != null && count > 0;
  }

  /** Throws DuplicateKeyException when the store already has the name or the slug. */
  public void create(
      UUID id, UUID tenant, UUID store, String name, String key, String slug, Instant now) {
    jdbc.update(
        """
        INSERT INTO catalog_brand(id,tenant_id,store_id,name,name_key,slug,created_at)
        VALUES (?,?,?,?,?,?,?)
        """,
        id,
        tenant,
        store,
        name,
        key,
        slug,
        Timestamp.from(now));
  }

  /** Renaming keeps the slug, like categories. */
  public boolean rename(UUID tenant, UUID brand, String name, String key) {
    return jdbc.update(
            "UPDATE catalog_brand SET name=?,name_key=? WHERE tenant_id=? AND id=?",
            name,
            key,
            tenant,
            brand)
        == 1;
  }

  /** Clears the brand from its products, then removes it; false when it does not exist. */
  public boolean delete(UUID tenant, UUID brand) {
    jdbc.update(
        "UPDATE catalog_product SET brand_id=NULL WHERE tenant_id=? AND brand_id=?", tenant, brand);
    return jdbc.update("DELETE FROM catalog_brand WHERE tenant_id=? AND id=?", tenant, brand) == 1;
  }
}
