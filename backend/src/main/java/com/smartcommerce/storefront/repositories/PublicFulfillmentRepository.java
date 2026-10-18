package com.smartcommerce.storefront.repositories;

import com.smartcommerce.storefront.domain.PublicFulfillment;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Public read model: only enabled options leave the database, and only for a published store. A
 * published store without saved configuration has no option.
 */
@Repository
public class PublicFulfillmentRepository {
  private final EntityManager entities;

  public PublicFulfillmentRepository(EntityManager entities) {
    this.entities = entities;
  }

  public Optional<PublicFulfillment> find(String slug) {
    List<?> rows =
        entities
            .createNativeQuery(
                """
      SELECT CASE WHEN f.pickup_enabled THEN f.pickup_instructions END,
             CASE WHEN f.fixed_shipping_enabled THEN f.fixed_shipping_amount END,
             CASE WHEN f.fixed_shipping_enabled THEN f.currency END,
             CASE WHEN f.fixed_shipping_enabled THEN f.fixed_shipping_area END
      FROM store s
      LEFT JOIN store_fulfillment f ON f.store_id=s.id AND f.tenant_id=s.tenant_id
      WHERE s.slug=:slug AND s.status='PUBLISHED'
      """)
            .setParameter("slug", slug)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] row = (Object[]) raw;
              return new PublicFulfillment(
                  (String) row[0], (BigDecimal) row[1], (String) row[2], (String) row[3]);
            })
        .findFirst();
  }
}
