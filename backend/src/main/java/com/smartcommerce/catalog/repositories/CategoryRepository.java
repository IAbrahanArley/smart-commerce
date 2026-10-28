package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.CategoryTree;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Product categories (MVP block 1c; tree since Sprint 8, SC-041). Every statement is scoped by the
 * tenant it is given; the caller has already checked the OWNER (and locked the store) through
 * {@link CatalogRepository}.
 */
@Repository
public class CategoryRepository {
  private final JdbcTemplate jdbc;

  /** A category as listed to the owner, before it is placed in the tree. */
  public record Row(
      UUID id,
      String name,
      String slug,
      UUID parent,
      int position,
      boolean published,
      long products) {}

  public CategoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Locks every category of the tenant (at most 50) in id order, so concurrent moves cannot build a
   * cycle or exceed the depth limit, and returns the tree they form.
   */
  public CategoryTree lockTree(UUID tenant) {
    return new CategoryTree(
        jdbc.query(
            "SELECT id,parent_id,position,name_key FROM catalog_category WHERE tenant_id=? ORDER BY id FOR UPDATE",
            (row, index) ->
                new CategoryTree.Node(
                    row.getObject("id", UUID.class),
                    row.getObject("parent_id", UUID.class),
                    row.getInt("position"),
                    row.getString("name_key")),
            tenant));
  }

  /** Categories with how many products have each as their category. */
  public Map<UUID, Row> rows(UUID tenant) {
    return jdbc
        .query(
            """
            SELECT c.id,c.name,c.slug,c.parent_id,c.position,c.published,
              (SELECT count(*) FROM catalog_product p WHERE p.tenant_id=c.tenant_id
                AND (p.category_id=c.id OR EXISTS (SELECT 1 FROM catalog_product_category pc
                  WHERE pc.product_id=p.id AND pc.tenant_id=p.tenant_id AND pc.category_id=c.id)))
                AS products
            FROM catalog_category c
            WHERE c.tenant_id=?
            """,
            (row, index) ->
                new Row(
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("slug"),
                    row.getObject("parent_id", UUID.class),
                    row.getInt("position"),
                    row.getBoolean("published"),
                    row.getLong("products")),
            tenant)
        .stream()
        .collect(Collectors.toMap(Row::id, row -> row));
  }

  public boolean slugTaken(UUID tenant, String slug, UUID except) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog_category WHERE tenant_id=? AND slug=? AND id IS DISTINCT FROM ?",
            Integer.class,
            tenant,
            slug,
            except);
    return count != null && count > 0;
  }

  /** Throws DuplicateKeyException when a sibling already has that name, or the slug is taken. */
  public void create(
      UUID id,
      UUID tenant,
      UUID store,
      UUID parent,
      String name,
      String key,
      String slug,
      int position,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO catalog_category(id,tenant_id,store_id,parent_id,name,name_key,slug,position,created_at)
        VALUES (?,?,?,?,?,?,?,?,?)
        """,
        id,
        tenant,
        store,
        parent,
        name,
        key,
        slug,
        position,
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

  /** Throws DuplicateKeyException when another category of the store has the slug. */
  public boolean changeSlug(UUID tenant, UUID category, String slug) {
    return jdbc.update(
            "UPDATE catalog_category SET slug=? WHERE tenant_id=? AND id=?", slug, tenant, category)
        == 1;
  }

  public boolean publish(UUID tenant, UUID category, boolean published) {
    return jdbc.update(
            "UPDATE catalog_category SET published=? WHERE tenant_id=? AND id=?",
            published,
            tenant,
            category)
        == 1;
  }

  /**
   * Moves the category under {@code parent} ({@code null}: root). Duplicate sibling name throws.
   */
  public void move(UUID tenant, UUID category, UUID parent) {
    jdbc.update(
        "UPDATE catalog_category SET parent_id=? WHERE tenant_id=? AND id=?",
        parent,
        tenant,
        category);
  }

  /** Gives each sibling its index in {@code order} as position. */
  public void reorder(UUID tenant, List<UUID> order) {
    for (int position = 0; position < order.size(); position++)
      jdbc.update(
          "UPDATE catalog_category SET position=? WHERE tenant_id=? AND id=?",
          position,
          tenant,
          order.get(position));
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

  /** Locks the product row; false when it is not a product of the tenant. */
  public boolean lockProduct(UUID tenant, UUID product) {
    return !jdbc.queryForList(
            "SELECT id FROM catalog_product WHERE tenant_id=? AND id=? FOR UPDATE",
            UUID.class,
            tenant,
            product)
        .isEmpty();
  }

  /**
   * Replaces the additional categories of a product (SC-043, V37). The composite foreign key
   * refuses a category of another tenant (DataIntegrityViolationException).
   */
  public void replaceAdditional(UUID tenant, UUID product, List<UUID> categories) {
    jdbc.update(
        "DELETE FROM catalog_product_category WHERE tenant_id=? AND product_id=?", tenant, product);
    for (UUID category : categories)
      jdbc.update(
          "INSERT INTO catalog_product_category(product_id,category_id,tenant_id) VALUES (?,?,?)",
          product,
          category,
          tenant);
  }

  /**
   * Sets ({@code category} present) or clears a product's category. The composite foreign key
   * refuses a category of another tenant (DataIntegrityViolationException).
   */
  public boolean assign(UUID tenant, UUID product, UUID category) {
    boolean assigned =
        jdbc.update(
                "UPDATE catalog_product SET category_id=? WHERE tenant_id=? AND id=?",
                category,
                tenant,
                product)
            == 1;
    // The primary category is never also an additional one (SC-043).
    if (assigned && category != null)
      jdbc.update(
          "DELETE FROM catalog_product_category WHERE tenant_id=? AND product_id=? AND category_id=?",
          tenant,
          product,
          category);
    return assigned;
  }

  /** Additional categories of each product (SC-043), in a stable order. */
  public Map<UUID, List<UUID>> additional(UUID tenant, List<UUID> products) {
    Map<UUID, List<UUID>> result = new java.util.HashMap<>();
    if (products.isEmpty()) return result;
    jdbc.query(
        "SELECT product_id,category_id FROM catalog_product_category WHERE tenant_id=? AND product_id = ANY (?) ORDER BY category_id",
        row -> {
          result
              .computeIfAbsent(row.getObject("product_id", UUID.class), key -> new ArrayList<>())
              .add(row.getObject("category_id", UUID.class));
        },
        tenant,
        products.toArray(new UUID[0]));
    return result;
  }
}
