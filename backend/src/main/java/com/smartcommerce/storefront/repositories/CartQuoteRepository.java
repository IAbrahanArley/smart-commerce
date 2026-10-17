package com.smartcommerce.storefront.repositories;

import com.smartcommerce.storefront.domain.QuotedVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Read-only cart quote projection; no writes, locks or reservations (ADR-0012 §2, D5). */
@Repository
public class CartQuoteRepository {
  /**
   * Sellable quantity per variant. Today it is the physical balance, matching the public rule "on
   * hand > 0"; SC-005 swaps this single expression for "on hand − active reservations".
   */
  static final String SELLABLE_QUANTITY = "COALESCE(b.quantity,0)";

  private final EntityManager entities;

  public CartQuoteRepository(EntityManager entities) {
    this.entities = entities;
  }

  /** Only variants of published products of the given published store; others are omitted. */
  public List<QuotedVariant> variants(String slug, Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    List<?> rows =
        entities
            .createNativeQuery(
                "SELECT v.id,p.id,p.name,v.price,v.currency,v.options_json,"
                    + SELLABLE_QUANTITY
                    + """

       FROM store s JOIN catalog_product p ON p.store_id=s.id AND p.tenant_id=s.tenant_id AND p.published
       JOIN catalog_variant v ON v.product_id=p.id AND v.tenant_id=p.tenant_id
       LEFT JOIN inventory_balance b ON b.product_id=v.id AND b.tenant_id=v.tenant_id
       WHERE s.slug=:slug AND s.status='PUBLISHED' AND v.id IN (:ids)
       """)
            .setParameter("slug", slug)
            .setParameter("ids", ids)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] r = (Object[]) raw;
              return new QuotedVariant(
                  (UUID) r[0],
                  (UUID) r[1],
                  (String) r[2],
                  (BigDecimal) r[3],
                  (String) r[4],
                  (String) r[5],
                  ((Number) r[6]).longValue());
            })
        .toList();
  }
}
