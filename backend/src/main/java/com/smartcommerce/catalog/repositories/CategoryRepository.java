package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.CategorySummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Product categories (MVP block 1c). Every statement is scoped by the tenant it is given; the
 * caller has already checked the OWNER (and locked the store) through {@link CatalogRepository}.
 */
@Repository
public class CategoryRepository {
  private final JdbcTemplate jdbc;

  public CategoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Categories with how many products each has, by name. */
  public List<CategorySummary> list(UUID tenant) {
    return jdbc.query(
        """
        SELECT c.id,c.name,count(p.id) AS products
        FROM catalog_category c
        LEFT JOIN catalog_product p ON p.category_id=c.id AND p.tenant_id=c.tenant_id
        WHERE c.tenant_id=?
        GROUP BY c.id,c.name,c.name_key
        ORDER BY c.name_key
        """,
        (row, index) ->
            new CategorySummary(
                row.getObject("id", UUID.class), row.getString("name"), row.getLong("products")),
        tenant);
  }

  public int count(UUID tenant) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog_category WHERE tenant_id=?", Integer.class, tenant);
    return count == null ? 0 : count;
  }

  /** Throws DuplicateKeyException when the store already has a category with that name. */
  public void create(UUID id, UUID tenant, UUID store, String name, String key, Instant now) {
    jdbc.update(
        """
        INSERT INTO catalog_category(id,tenant_id,store_id,name,name_key,created_at)
        VALUES (?,?,?,?,?,?)
        """,
        id,
        tenant,
        store,
        name,
        key,
        Timestamp.from(now));
  }

  public boolean rename(UUID tenant, UUID category, String name, String key) {
    return jdbc.update(
            "UPDATE catalog_category SET name=?,name_key=? WHERE tenant_id=? AND id=?",
            name,
            key,
            tenant,
            category)
        == 1;
  }

  /** Clears the category from its products, then removes it; false when it does not exist. */
  public boolean delete(UUID tenant, UUID category) {
    jdbc.update(
        "UPDATE catalog_product SET category_id=NULL WHERE tenant_id=? AND category_id=?",
        tenant,
        category);
    return jdbc.update("DELETE FROM catalog_category WHERE tenant_id=? AND id=?", tenant, category)
        == 1;
  }

  /**
   * Sets ({@code category} present) or clears a product's category. The composite foreign key
   * refuses a category of another tenant (DataIntegrityViolationException).
   */
  public boolean assign(UUID tenant, UUID product, UUID category) {
    return jdbc.update(
            "UPDATE catalog_product SET category_id=? WHERE tenant_id=? AND id=?",
            category,
            tenant,
            product)
        == 1;
  }
}
