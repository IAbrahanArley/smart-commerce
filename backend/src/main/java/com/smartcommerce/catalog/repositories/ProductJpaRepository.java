package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.entities.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Package-private, scoped operations only; no global findAll/findById are exposed. */
interface ProductJpaRepository extends Repository<Product, UUID> {
  String OWNED =
      """
 SELECT p.* FROM catalog_product p
 JOIN store s ON s.id = p.store_id AND s.tenant_id = p.tenant_id
 JOIN tenant_membership m ON m.tenant_id = p.tenant_id AND m.user_id = :actor AND m.role = 'OWNER'
 JOIN identity_user u ON u.id = m.user_id AND u.status = 'ACTIVE' AND u.verified_at IS NOT NULL
 WHERE p.tenant_id = :tenant
 """;

  @Query(value = OWNED + " ORDER BY p.created_at,p.id", nativeQuery = true)
  List<Product> listOwned(@Param("actor") UUID actor, @Param("tenant") UUID tenant);

  @Query(value = OWNED + " AND p.id = :product", nativeQuery = true)
  Optional<Product> findOwned(
      @Param("actor") UUID actor, @Param("tenant") UUID tenant, @Param("product") UUID product);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
 UPDATE catalog_product p SET name=:name,sku=:sku,price=:price,cost=:cost,
 description_html=COALESCE(:description,p.description_html),version=p.version+1,updated_at=GREATEST(p.updated_at,:now)
 WHERE p.id=:product AND p.tenant_id=:tenant AND p.version=:version
 AND EXISTS (SELECT 1 FROM tenant_membership m
 JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
 JOIN store s ON s.tenant_id=m.tenant_id AND s.id=p.store_id
 WHERE m.tenant_id=p.tenant_id AND m.user_id=:actor AND m.role='OWNER')
 """,
      nativeQuery = true)
  int updateOwned(
      @Param("actor") UUID actor,
      @Param("tenant") UUID tenant,
      @Param("product") UUID product,
      @Param("name") String name,
      @Param("sku") String sku,
      @Param("price") BigDecimal price,
      @Param("cost") BigDecimal cost,
      @Param("version") long version,
      @Param("now") Instant now,
      @Param("description") String description);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
   UPDATE catalog_product p SET published=:published,version=p.version+1,
       updated_at=GREATEST(p.updated_at,:now)
   WHERE p.id=:product AND p.tenant_id=:tenant AND p.version=:version
     AND EXISTS (SELECT 1 FROM tenant_membership m
       JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
       JOIN store s ON s.id=p.store_id AND s.tenant_id=m.tenant_id
       WHERE m.tenant_id=p.tenant_id AND m.user_id=:actor AND m.role='OWNER')
   """,
      nativeQuery = true)
  int publishOwned(
      @Param("actor") UUID actor,
      @Param("tenant") UUID tenant,
      @Param("product") UUID product,
      @Param("published") boolean published,
      @Param("version") long version,
      @Param("now") Instant now);
}
