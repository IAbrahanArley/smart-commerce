package com.smartcommerce.storefront.repositories;

import com.smartcommerce.storefront.domain.PublicProduct;
import com.smartcommerce.storefront.domain.PublicStore;
import com.smartcommerce.storefront.domain.PublicVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * Public read model: projections whitelist public columns and never load owner/domain entities.
 * Availability uses the inventory SQL function (physical minus ACTIVE, unexpired reservations at
 * {@code now}); never read {@code inventory_balance.quantity} directly here.
 */
@Repository
public class StorefrontRepository {
  private static final String PRODUCTS =
      """
   SELECT p.id,p.name,COALESCE((SELECT MIN(v.price) FROM catalog_variant v WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id),p.price),p.currency,
    EXISTS(SELECT 1 FROM catalog_variant v WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id
       AND inventory_available_quantity(v.tenant_id,v.id,:now)>0) AS available,p.description_html,p.kind
   FROM store s
   JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
   WHERE s.slug=:slug AND s.status='PUBLISHED'
   """;
  private final EntityManager entities;

  public StorefrontRepository(EntityManager entities) {
    this.entities = entities;
  }

  /** The storefront address of the tenant's store, published or not (SC-047 revalidation). */
  public Optional<String> slugOf(UUID tenant) {
    return entities
        .createNativeQuery("SELECT slug FROM store WHERE tenant_id=:tenant")
        .setParameter("tenant", tenant)
        .getResultList()
        .stream()
        .map(String.class::cast)
        .findFirst();
  }

  public Optional<PublicStore> store(String slug) {
    List<?> rows =
        entities
            .createNativeQuery(
                """
                SELECT s.name,s.slug,p.contact_email,p.whatsapp,p.instagram,
                  p.logo_content IS NOT NULL,p.logo_width,p.logo_height,
                  CASE WHEN EXISTS(SELECT 1 FROM payment_account a
                    WHERE a.tenant_id=s.tenant_id AND a.status='CONNECTED') THEN s.max_installments END
                FROM store s LEFT JOIN store_profile p ON p.store_id=s.id AND p.tenant_id=s.tenant_id
                WHERE s.slug=:slug AND s.status='PUBLISHED'
                """)
            .setParameter("slug", slug)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] row = (Object[]) raw;
              return new PublicStore(
                  (String) row[0],
                  (String) row[1],
                  (String) row[2],
                  (String) row[3],
                  (String) row[4],
                  Boolean.TRUE.equals(row[5]),
                  row[6] == null ? null : ((Number) row[6]).intValue(),
                  row[7] == null ? null : ((Number) row[7]).intValue(),
                  row[8] == null ? null : ((Number) row[8]).intValue());
            })
        .findFirst();
  }

  /** The logo of a published store (MVP block 1b); empty when hidden or absent. */
  public Optional<StoreLogo> logo(String slug) {
    List<?> rows =
        entities
            .createNativeQuery(
                """
                SELECT p.logo_content,p.logo_media_type FROM store s
                JOIN store_profile p ON p.store_id=s.id AND p.tenant_id=s.tenant_id
                WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.logo_content IS NOT NULL
                """)
            .setParameter("slug", slug)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] row = (Object[]) raw;
              return new StoreLogo((byte[]) row[0], (String) row[1]);
            })
        .findFirst();
  }

  public record StoreLogo(byte[] bytes, String mediaType) {}

  /** Categories of a published store that have at least one published product, by name. */
  public List<Object[]> categories(String slug) {
    List<?> rows =
        entities
            .createNativeQuery(
                """
                SELECT c.id,c.name FROM store s
                JOIN catalog_category c ON c.store_id=s.id AND c.tenant_id=s.tenant_id
                WHERE s.slug=:slug AND s.status='PUBLISHED' AND c.published AND EXISTS (
                  SELECT 1 FROM catalog_product p
                  WHERE p.tenant_id=c.tenant_id AND p.published AND (p.category_id=c.id
                    OR EXISTS (SELECT 1 FROM catalog_product_category pc
                      WHERE pc.product_id=p.id AND pc.tenant_id=p.tenant_id AND pc.category_id=c.id)))
                ORDER BY c.name_key
                """)
            .setParameter("slug", slug)
            .getResultList();
    return rows.stream().map(row -> (Object[]) row).toList();
  }

  public List<PublicProduct> list(String slug, UUID after, Instant now) {
    return list(slug, after, now, null);
  }

  /** One page of published products, optionally of one category (MVP block 1c). */
  public List<PublicProduct> list(String slug, UUID after, Instant now, UUID category) {
    var query =
        entities
            .createNativeQuery(
                PRODUCTS
                    + (after == null ? "" : " AND p.id>:after")
                    + (category == null
                        ? ""
                        // A hidden category (SC-041) filters nothing in: its products stay public.
                        // Primary or additional category (SC-043).
                        : " AND (p.category_id=:category OR EXISTS (SELECT 1 FROM"
                            + " catalog_product_category pc WHERE pc.product_id=p.id"
                            + " AND pc.tenant_id=p.tenant_id AND pc.category_id=:category))"
                            + " AND EXISTS (SELECT 1 FROM catalog_category c WHERE c.id=:category"
                            + " AND c.tenant_id=p.tenant_id AND c.published)")
                    + " ORDER BY p.id")
            .setParameter("slug", slug)
            .setParameter("now", utc(now))
            .setMaxResults(21);
    if (after != null) query.setParameter("after", after);
    if (category != null) query.setParameter("category", category);
    List<?> rows = query.getResultList();
    return rows.stream().map(StorefrontRepository::product).toList();
  }

  public Optional<PublicProduct> find(String slug, UUID product, Instant now) {
    List<?> rows =
        entities
            .createNativeQuery(PRODUCTS + " AND p.id=:product")
            .setParameter("slug", slug)
            .setParameter("now", utc(now))
            .setParameter("product", product)
            .getResultList();
    return rows.stream().map(StorefrontRepository::product).findFirst();
  }

  private static OffsetDateTime utc(Instant now) {
    return now.atOffset(ZoneOffset.UTC);
  }

  private static PublicProduct product(Object raw) {
    Object[] row = (Object[]) raw;
    return new PublicProduct(
        (UUID) row[0],
        (String) row[1],
        (BigDecimal) row[2],
        (String) row[3],
        Boolean.TRUE.equals(row[4]),
        (String) row[5],
        (String) row[6]);
  }

  public List<PublicVariant> variants(String slug, UUID product, Instant now) {
    List<?> rows =
        entities
            .createNativeQuery(
                """
      SELECT v.id,v.price,v.currency,inventory_available_quantity(v.tenant_id,v.id,:now)>0,v.options_json
      FROM store s JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
      JOIN catalog_variant v ON v.product_id=p.id AND v.tenant_id=p.tenant_id
      WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.id=:product ORDER BY v.id
      """)
            .setParameter("slug", slug)
            .setParameter("product", product)
            .setParameter("now", utc(now))
            .setMaxResults(50)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] r = (Object[]) raw;
              return new PublicVariant(
                  (UUID) r[0],
                  (BigDecimal) r[1],
                  (String) r[2],
                  Boolean.TRUE.equals(r[3]),
                  (String) r[4]);
            })
        .toList();
  }
}
