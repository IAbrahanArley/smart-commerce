package com.smartcommerce.storefront.repositories;

import com.smartcommerce.storefront.domain.PublicProduct;
import com.smartcommerce.storefront.domain.PublicStore;
import com.smartcommerce.storefront.domain.PublicVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Public read model: projections whitelist public columns and never load owner/domain entities. */
@Repository
public class StorefrontRepository {
  private static final String PRODUCTS =
      """
   SELECT p.id,p.name,COALESCE((SELECT MIN(v.price) FROM catalog_variant v WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id),p.price),p.currency,
    EXISTS(SELECT 1 FROM catalog_variant v JOIN inventory_balance b ON b.product_id=v.id AND b.tenant_id=v.tenant_id
       WHERE v.product_id=p.id AND v.tenant_id=p.tenant_id AND b.quantity>0) AS available,p.description_html,p.kind
   FROM store s
   JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
   WHERE s.slug=:slug AND s.status='PUBLISHED'
   """;
  private final EntityManager entities;

  public StorefrontRepository(EntityManager entities) {
    this.entities = entities;
  }

  public Optional<PublicStore> store(String slug) {
    List<?> rows =
        entities
            .createNativeQuery(
                "SELECT s.name,s.slug FROM store s WHERE s.slug=:slug AND s.status='PUBLISHED'")
            .setParameter("slug", slug)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] row = (Object[]) raw;
              return new PublicStore((String) row[0], (String) row[1]);
            })
        .findFirst();
  }

  public List<PublicProduct> list(String slug, UUID after) {
    var query =
        entities
            .createNativeQuery(
                PRODUCTS + (after == null ? "" : " AND p.id>:after") + " ORDER BY p.id")
            .setParameter("slug", slug)
            .setMaxResults(21);
    if (after != null) query.setParameter("after", after);
    List<?> rows = query.getResultList();
    return rows.stream().map(StorefrontRepository::product).toList();
  }

  public Optional<PublicProduct> find(String slug, UUID product) {
    List<?> rows =
        entities
            .createNativeQuery(PRODUCTS + " AND p.id=:product")
            .setParameter("slug", slug)
            .setParameter("product", product)
            .getResultList();
    return rows.stream().map(StorefrontRepository::product).findFirst();
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

  public List<PublicVariant> variants(String slug, UUID product) {
    List<?> rows =
        entities
            .createNativeQuery(
                """
      SELECT v.id,v.price,v.currency,COALESCE(b.quantity,0)>0,v.options_json
      FROM store s JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
      JOIN catalog_variant v ON v.product_id=p.id AND v.tenant_id=p.tenant_id
      LEFT JOIN inventory_balance b ON b.product_id=v.id AND b.tenant_id=v.tenant_id
      WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.id=:product ORDER BY v.id
      """)
            .setParameter("slug", slug)
            .setParameter("product", product)
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
