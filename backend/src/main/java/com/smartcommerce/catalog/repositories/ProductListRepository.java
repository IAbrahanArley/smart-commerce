package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.ProductListQuery;
import com.smartcommerce.catalog.domain.ProductPageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The admin product list (filtered, ordered and paged in SQL) for the store OWNER only, like the
 * other catalog reads. Returns ids and counts; the products themselves load through JPA.
 */
@Repository
public class ProductListRepository {
  // Each fragment starts with a space, so they concatenate into valid SQL.
  private static final String OWNED =
      " FROM catalog_product p"
          + " JOIN store s ON s.id=p.store_id AND s.tenant_id=p.tenant_id"
          + " JOIN tenant_membership m ON m.tenant_id=p.tenant_id AND m.user_id=? AND m.role='OWNER'"
          + " JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE'"
          + " AND u.verified_at IS NOT NULL"
          + " WHERE p.tenant_id=?";
  private static final String HAYSTACK =
      " sc_unaccent(lower(p.name || ' ' || p.sku || ' ' || COALESCE((SELECT string_agg(v.sku, ' ')"
          + " FROM catalog_variant v WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id), '')))";

  private final JdbcTemplate jdbc;

  public ProductListRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public ProductPageResult page(UUID actor, UUID tenant, ProductListQuery query) {
    StringBuilder filter = new StringBuilder();
    List<Object> args = new ArrayList<>(List.of(actor, tenant));
    for (String word : query.words()) {
      filter.append(" AND").append(HAYSTACK).append(" LIKE ?");
      args.add("%" + escape(word) + "%");
    }
    if (query.withoutCategory()) filter.append(" AND p.category_id IS NULL");
    else if (query.category() != null) {
      filter.append(" AND p.category_id=?");
      args.add(query.category());
    }
    switch (query.visibility()) {
      case VISIBLE -> filter.append(" AND p.published");
      case HIDDEN -> filter.append(" AND NOT p.published");
      case ALL -> {}
    }
    String order =
        switch (query.sort()) {
          case NAME -> "sc_unaccent(lower(p.name)), p.created_at, p.id";
          case PRICE_ASC -> "p.price, p.created_at, p.id";
          case PRICE_DESC -> "p.price DESC, p.created_at, p.id";
          case DEFAULT -> "p.created_at, p.id";
        };
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(ProductListQuery.PAGE_SIZE);
    pageArgs.add((long) (query.page() - 1) * ProductListQuery.PAGE_SIZE);
    List<UUID> ids =
        jdbc.queryForList(
            "SELECT p.id" + OWNED + filter + " ORDER BY " + order + " LIMIT ? OFFSET ?",
            UUID.class,
            pageArgs.toArray());
    Long matching =
        jdbc.queryForObject("SELECT count(*)" + OWNED + filter, Long.class, args.toArray());
    var totals =
        jdbc.queryForMap(
            "SELECT count(*) AS catalog, count(*) FILTER (WHERE p.published) AS published" + OWNED,
            actor,
            tenant);
    return new ProductPageResult(
        ids,
        matching == null ? 0 : matching,
        ((Number) totals.get("catalog")).longValue(),
        ((Number) totals.get("published")).longValue());
  }

  /** The search word as a literal LIKE fragment: backslash, percent and underscore are escaped. */
  private static String escape(String word) {
    return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
