package com.smartcommerce.storefront.repositories;

import com.smartcommerce.storefront.domain.CatalogQuery;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Public read model of the storefront catalog (Sprint 8, SC-042, ADR-0018 V6/V7). Every statement
 * starts from the published store resolved by its slug and only sees its published products, so no
 * count, facet or row ever mixes stores. The SQL text is built from fixed fragments; buyer input
 * (already validated by {@link CatalogQuery}) only travels as bound parameters.
 */
@Repository
public class PublicCatalogRepository {
  /** Price buckets of the price facet: below 50, 50 to 99.99, ..., 5000 or more. */
  public static final List<BigDecimal> PRICE_EDGES =
      List.of(50, 100, 200, 300, 500, 1000, 2000, 5000).stream().map(BigDecimal::valueOf).toList();

  private static final String PRICE = "COALESCE(pr.price,p.price)";
  private static final String FROM =
      """
      FROM store s
      JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
      LEFT JOIN catalog_brand b ON b.id=p.brand_id AND b.tenant_id=p.tenant_id
      CROSS JOIN LATERAL (SELECT min(v.price) AS price FROM catalog_variant v
        WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id) pr
      WHERE s.slug=:slug AND s.status='PUBLISHED'
      """;
  private static final String MATCHES =
      """
       AND (to_tsvector('portuguese', sc_unaccent(p.name)) @@ to_tsquery('portuguese', :search)
        OR to_tsvector('portuguese', sc_unaccent(coalesce(b.name,''))) @@ to_tsquery('portuguese', :search))
      """;

  /** Units sold in paid orders since {@code :since}, then the most recent (ADR-0018 V7). */
  private static final String BEST_SELLING =
      """
      (SELECT coalesce(sum(i.quantity),0) FROM sales_order_item i
        JOIN sales_order o ON o.id=i.order_id AND o.tenant_id=i.tenant_id
        WHERE i.product_id=p.id AND i.tenant_id=p.tenant_id AND o.status='PAID'
          AND o.paid_at>=:since) DESC,p.created_at DESC,p.id""";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final NamedParameterJdbcTemplate jdbc;

  /** A published category of the store, for the menu and the category filter. */
  public record Category(UUID id, UUID parent, String name, String slug, int position) {}

  /** One product card of a catalog page. */
  public record Card(
      UUID id,
      String slug,
      String name,
      String brand,
      BigDecimal price,
      BigDecimal compareAt,
      String currency,
      boolean available) {}

  public record Count(String key, String label, long count) {}

  public record OptionCount(String name, String value, long count) {}

  /** The filters the facet being counted ignores, so it can offer the other choices. */
  public enum Facet {
    NONE,
    CATEGORY,
    BRAND,
    PRICE,
    OPTIONS
  }

  public PublicCatalogRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Published categories of a published store, in menu order. */
  public List<Category> categories(String slug) {
    return jdbc.query(
        """
        SELECT c.id,c.parent_id,c.name,c.slug,c.position FROM store s
        JOIN catalog_category c ON c.store_id=s.id AND c.tenant_id=s.tenant_id AND c.published
        WHERE s.slug=:slug AND s.status='PUBLISHED'
        ORDER BY c.position,c.name_key,c.id
        """,
        new MapSqlParameterSource("slug", slug),
        (row, index) ->
            new Category(
                row.getObject("id", UUID.class),
                row.getObject("parent_id", UUID.class),
                row.getString("name"),
                row.getString("slug"),
                row.getInt("position")));
  }

  /** One page of product cards. {@code categories} null means every category. */
  public List<Card> page(
      String slug, CatalogQuery query, Collection<UUID> categories, Instant now) {
    MapSqlParameterSource params = params(slug, query, categories, Facet.NONE);
    params.addValue("now", Timestamp.from(now));
    params.addValue("since", Timestamp.from(now.minus(90, ChronoUnit.DAYS)));
    params.addValue("limit", CatalogQuery.PAGE_SIZE);
    params.addValue("offset", query.offset());
    String sql =
        """
        SELECT p.id,p.slug,p.name,b.name AS brand,%s AS price,p.currency,
          (SELECT v.compare_at_price FROM catalog_variant v WHERE v.product_id=p.id
            AND v.tenant_id=p.tenant_id ORDER BY v.price,v.id LIMIT 1) AS compare_at,
          EXISTS(SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id
            AND inventory_available_quantity(v.tenant_id,v.id,:now)>0) AS available
        """
                .formatted(PRICE)
            + FROM
            + where(query, categories, Facet.NONE)
            + " ORDER BY "
            + order(query)
            + " LIMIT :limit OFFSET :offset";
    return jdbc.query(
        sql,
        params,
        (row, index) ->
            new Card(
                row.getObject("id", UUID.class),
                row.getString("slug"),
                row.getString("name"),
                row.getString("brand"),
                row.getBigDecimal("price"),
                row.getBigDecimal("compare_at"),
                row.getString("currency"),
                row.getBoolean("available")));
  }

  /** Storefront details of a published product: address, brand and primary category. */
  public record Details(UUID id, String slug, String brand, String brandSlug, UUID category) {}

  public java.util.Optional<Details> details(String slug, UUID product) {
    return jdbc
        .query(
            """
            SELECT p.id,p.slug,b.name AS brand,b.slug AS brand_slug,p.category_id
            FROM store s
            JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
            LEFT JOIN catalog_brand b ON b.id=p.brand_id AND b.tenant_id=p.tenant_id
            WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.id=:product
            """,
            new MapSqlParameterSource("slug", slug).addValue("product", product),
            (row, index) ->
                new Details(
                    row.getObject("id", UUID.class),
                    row.getString("slug"),
                    row.getString("brand"),
                    row.getString("brand_slug"),
                    row.getObject("category_id", UUID.class)))
        .stream()
        .findFirst();
  }

  /** The published product with this address, if any. */
  public java.util.Optional<UUID> productBySlug(String slug, String productSlug) {
    return jdbc
        .queryForList(
            """
            SELECT p.id FROM store s
            JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
            WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.slug=:product
            """,
            new MapSqlParameterSource("slug", slug).addValue("product", productSlug),
            UUID.class)
        .stream()
        .findFirst();
  }

  /**
   * The current address of a published product that used {@code productSlug} until less than 90
   * days ago (SC-041 history), so old links can be redirected.
   */
  public java.util.Optional<String> movedTo(String slug, String productSlug, Instant now) {
    return jdbc
        .queryForList(
            """
            SELECT p.slug FROM store s
            JOIN catalog_product_slug_history h ON h.store_id=s.id AND h.tenant_id=s.tenant_id
            JOIN catalog_product p ON p.id=h.product_id AND p.tenant_id=h.tenant_id AND p.published
            WHERE s.slug=:slug AND s.status='PUBLISHED' AND h.slug=:product
              AND h.replaced_at>=:since AND p.slug IS NOT NULL
            """,
            new MapSqlParameterSource("slug", slug)
                .addValue("product", productSlug)
                .addValue("since", Timestamp.from(now.minus(90, ChronoUnit.DAYS))),
            String.class)
        .stream()
        .findFirst();
  }

  /**
   * Up to 8 available published products of the same primary category, best sellers of the last 90
   * days first, then the most recent.
   */
  public List<Card> related(String slug, UUID category, UUID product, Instant now) {
    MapSqlParameterSource params =
        new MapSqlParameterSource("slug", slug)
            .addValue("category", category)
            .addValue("product", product)
            .addValue("now", Timestamp.from(now))
            .addValue("since", Timestamp.from(now.minus(90, ChronoUnit.DAYS)));
    return jdbc.query(
        """
        SELECT p.id,p.slug,p.name,b.name AS brand,%s AS price,p.currency,
          (SELECT v.compare_at_price FROM catalog_variant v WHERE v.product_id=p.id
            AND v.tenant_id=p.tenant_id ORDER BY v.price,v.id LIMIT 1) AS compare_at,
          true AS available
        """
                .formatted(PRICE)
            + FROM
            + """
             AND p.category_id=:category AND p.id<>:product
             AND EXISTS(SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id
               AND inventory_available_quantity(v.tenant_id,v.id,:now)>0)
            ORDER BY """
            + BEST_SELLING
            + " LIMIT 8",
        params,
        (row, index) ->
            new Card(
                row.getObject("id", UUID.class),
                row.getString("slug"),
                row.getString("name"),
                row.getString("brand"),
                row.getBigDecimal("price"),
                row.getBigDecimal("compare_at"),
                row.getString("currency"),
                row.getBoolean("available")));
  }

  /** "Compare at" price of each variant of a published product. */
  public Map<UUID, BigDecimal> compareAt(String slug, UUID product) {
    Map<UUID, BigDecimal> prices = new LinkedHashMap<>();
    jdbc.query(
        """
        SELECT v.id,v.compare_at_price FROM store s
        JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
        JOIN catalog_variant v ON v.product_id=p.id AND v.tenant_id=p.tenant_id
        WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.id=:product AND v.compare_at_price IS NOT NULL
        """,
        new MapSqlParameterSource("slug", slug).addValue("product", product),
        row -> {
          prices.put(row.getObject("id", UUID.class), row.getBigDecimal("compare_at_price"));
        });
    return prices;
  }

  public long count(String slug, CatalogQuery query, Collection<UUID> categories) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) " + FROM + where(query, categories, Facet.NONE),
            params(slug, query, categories, Facet.NONE),
            Long.class);
    return count == null ? 0 : count;
  }

  /** Products per category, every filter applied except the category itself. */
  public Map<UUID, Long> categoryCounts(String slug, CatalogQuery query) {
    Map<UUID, Long> counts = new LinkedHashMap<>();
    jdbc.query(
        "SELECT p.category_id,count(*) AS n "
            + FROM
            + where(query, null, Facet.CATEGORY)
            + " AND p.category_id IS NOT NULL GROUP BY p.category_id",
        params(slug, query, null, Facet.CATEGORY),
        row -> {
          counts.put(row.getObject("category_id", UUID.class), row.getLong("n"));
        });
    return counts;
  }

  /** Products per brand, every filter applied except the brands, by brand name. */
  public List<Count> brandCounts(String slug, CatalogQuery query, Collection<UUID> categories) {
    return jdbc.query(
        "SELECT b.slug,b.name,count(*) AS n "
            + FROM
            + where(query, categories, Facet.BRAND)
            + " AND b.id IS NOT NULL GROUP BY b.slug,b.name,b.name_key ORDER BY b.name_key",
        params(slug, query, categories, Facet.BRAND),
        (row, index) -> new Count(row.getString("slug"), row.getString("name"), row.getLong("n")));
  }

  /**
   * Products per price bucket ({@link #PRICE_EDGES}), every filter applied except the price. The
   * key is the bucket index: 0 is below the first edge.
   */
  public Map<Integer, Long> priceCounts(
      String slug, CatalogQuery query, Collection<UUID> categories) {
    Map<Integer, Long> counts = new LinkedHashMap<>();
    String edges = String.join(",", PRICE_EDGES.stream().map(BigDecimal::toPlainString).toList());
    jdbc.query(
        "SELECT width_bucket(%s, ARRAY[%s]::numeric[]) AS bucket,count(*) AS n "
                .formatted(PRICE, edges)
            + FROM
            + where(query, categories, Facet.PRICE)
            + " GROUP BY bucket ORDER BY bucket",
        params(slug, query, categories, Facet.PRICE),
        row -> {
          counts.put(row.getInt("bucket"), row.getLong("n"));
        });
    return counts;
  }

  /** Products per variant option value, every filter applied except the options. */
  public List<OptionCount> optionCounts(
      String slug, CatalogQuery query, Collection<UUID> categories) {
    return jdbc.query(
        """
        SELECT o->>'name' AS name,o->>'value' AS value,count(DISTINCT p.id) AS n
        FROM (SELECT p.id,p.tenant_id
        """
            + FROM
            + where(query, categories, Facet.OPTIONS)
            + """
            ) p
            JOIN catalog_variant v ON v.product_id=p.id AND v.tenant_id=p.tenant_id
            CROSS JOIN LATERAL jsonb_array_elements(v.options_json::jsonb) o
            GROUP BY 1,2 ORDER BY 1,2 LIMIT 300
            """,
        params(slug, query, categories, Facet.OPTIONS),
        (row, index) ->
            new OptionCount(row.getString("name"), row.getString("value"), row.getLong("n")));
  }

  private static String where(CatalogQuery query, Collection<UUID> categories, Facet facet) {
    StringBuilder sql = new StringBuilder();
    if (categories != null && facet != Facet.CATEGORY)
      sql.append(" AND p.category_id IN (:categories)");
    if (query.search() != null) sql.append(MATCHES);
    if (!query.brands().isEmpty() && facet != Facet.BRAND) sql.append(" AND b.slug IN (:brands)");
    if (facet != Facet.PRICE) {
      if (query.priceMin() != null) sql.append(" AND ").append(PRICE).append(">=:priceMin");
      if (query.priceMax() != null) sql.append(" AND ").append(PRICE).append("<=:priceMax");
    }
    if (facet != Facet.OPTIONS) {
      int name = 0;
      for (List<String> values : query.options().values()) {
        List<String> any = new ArrayList<>();
        for (int value = 0; value < values.size(); value++)
          any.add("v.options_json::jsonb @> CAST(:option" + name + "_" + value + " AS jsonb)");
        sql.append(
            " AND EXISTS (SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id"
                + " AND v.tenant_id=p.tenant_id AND ("
                + String.join(" OR ", any)
                + "))");
        name++;
      }
    }
    return sql.toString();
  }

  private static MapSqlParameterSource params(
      String slug, CatalogQuery query, Collection<UUID> categories, Facet facet) {
    MapSqlParameterSource params = new MapSqlParameterSource("slug", slug);
    if (categories != null && facet != Facet.CATEGORY) params.addValue("categories", categories);
    if (query.search() != null) params.addValue("search", query.search());
    if (!query.brands().isEmpty()) params.addValue("brands", query.brands());
    if (query.priceMin() != null) params.addValue("priceMin", query.priceMin());
    if (query.priceMax() != null) params.addValue("priceMax", query.priceMax());
    int name = 0;
    for (Map.Entry<String, List<String>> option : query.options().entrySet()) {
      for (int value = 0; value < option.getValue().size(); value++)
        params.addValue(
            "option" + name + "_" + value,
            JSON.writeValueAsString(
                List.of(Map.of("name", option.getKey(), "value", option.getValue().get(value)))));
      name++;
    }
    return params;
  }

  private static String order(CatalogQuery query) {
    return switch (query.sort()) {
      case RELEVANCE ->
          "ts_rank(to_tsvector('portuguese', sc_unaccent(p.name)), to_tsquery('portuguese', :search)) DESC,p.created_at DESC,p.id";
      case RECENT -> "p.created_at DESC,p.id";
      case BEST_SELLING -> BEST_SELLING;
      case PRICE_ASC -> PRICE + " ASC,p.id";
      case PRICE_DESC -> PRICE + " DESC,p.id";
      case DISCOUNT ->
          """
          (SELECT max((v.compare_at_price-v.price)/v.compare_at_price) FROM catalog_variant v
            WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id AND v.compare_at_price>v.price)
            DESC NULLS LAST,p.id""";
    };
  }
}
