package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ProductImageRepository {
  private final EntityManager entities;

  public ProductImageRepository(EntityManager entities) {
    this.entities = entities;
  }

  public long lock(UUID tenant, UUID product) {
    List<?> rows =
        entities
            .createNativeQuery(
                "SELECT version FROM catalog_product WHERE tenant_id=:tenant AND id=:product FOR UPDATE")
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .getResultList();
    if (rows.isEmpty()) throw missing();
    return ((Number) rows.getFirst()).longValue();
  }

  public void bump(UUID tenant, UUID product, long version) {
    int updated =
        entities
            .createNativeQuery(
                "UPDATE catalog_product SET version=version+1,updated_at=:now WHERE tenant_id=:tenant AND id=:product AND version=:version")
            .setParameter("now", Instant.now())
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .setParameter("version", version)
            .executeUpdate();
    if (updated != 1)
      throw new ImageException(
          ImageException.Reason.VERSION_CONFLICT,
          "O produto foi alterado. Carregue a versão atual.");
    entities.clear();
  }

  /**
   * Cover (first by position) and photo count of each product, in one query for a whole list.
   * Products without photos are absent from the map.
   */
  public Map<UUID, ProductImageSummary> summaries(UUID tenant, Collection<UUID> products) {
    if (products.isEmpty()) return Map.of();
    List<?> rows =
        entities
            .createNativeQuery(
                """
                SELECT DISTINCT ON (product_id) product_id, id, alt,
                  count(*) OVER (PARTITION BY product_id)
                FROM catalog_product_image WHERE tenant_id=:tenant AND product_id IN (:products)
                ORDER BY product_id, position, id
                """)
            .setParameter("tenant", tenant)
            .setParameter("products", products)
            .getResultList();
    Map<UUID, ProductImageSummary> summaries = new HashMap<>();
    for (Object raw : rows) {
      Object[] r = (Object[]) raw;
      summaries.put(
          (UUID) r[0],
          new ProductImageSummary((UUID) r[1], (String) r[2], ((Number) r[3]).intValue()));
    }
    return summaries;
  }

  public List<ProductImageData> list(UUID tenant, UUID product) {
    List<?> rows =
        entities
            .createNativeQuery(
                "SELECT id,alt,width,height FROM catalog_product_image WHERE tenant_id=:tenant AND product_id=:product ORDER BY position,id")
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .getResultList();
    return rows.stream()
        .map(
            raw -> {
              Object[] r = (Object[]) raw;
              return new ProductImageData(
                  (UUID) r[0],
                  (String) r[1],
                  ((Number) r[2]).intValue(),
                  ((Number) r[3]).intValue());
            })
        .toList();
  }

  public void insert(
      UUID id, UUID tenant, UUID product, String alt, int position, ImageContent content) {
    entities
        .createNativeQuery(
            "INSERT INTO catalog_product_image(id,tenant_id,product_id,alt,position,width,height,media_type,content) VALUES(:id,:tenant,:product,:alt,:position,:width,:height,:type,:bytes)")
        .setParameter("id", id)
        .setParameter("tenant", tenant)
        .setParameter("product", product)
        .setParameter("alt", alt)
        .setParameter("position", position)
        .setParameter("width", content.width())
        .setParameter("height", content.height())
        .setParameter("type", content.mediaType())
        .setParameter("bytes", content.bytes())
        .executeUpdate();
  }

  public void position(UUID tenant, UUID product, UUID image, int position) {
    entities
        .createNativeQuery(
            "UPDATE catalog_product_image SET position=:position WHERE tenant_id=:tenant AND product_id=:product AND id=:image")
        .setParameter("position", position)
        .setParameter("tenant", tenant)
        .setParameter("product", product)
        .setParameter("image", image)
        .executeUpdate();
  }

  public void remove(UUID tenant, UUID product, UUID image) {
    if (entities
            .createNativeQuery(
                "DELETE FROM catalog_product_image WHERE tenant_id=:tenant AND product_id=:product AND id=:image")
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .setParameter("image", image)
            .executeUpdate()
        != 1) throw missing();
  }

  public ImageContent content(UUID tenant, UUID product, UUID image) {
    List<?> rows =
        entities
            .createNativeQuery(
                "SELECT content,media_type,width,height FROM catalog_product_image WHERE tenant_id=:tenant AND product_id=:product AND id=:image")
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .setParameter("image", image)
            .getResultList();
    if (rows.isEmpty()) throw missing();
    Object[] r = (Object[]) rows.getFirst();
    return new ImageContent(
        (byte[]) r[0], (String) r[1], ((Number) r[2]).intValue(), ((Number) r[3]).intValue());
  }

  public UUID publishedTenant(String slug, UUID product) {
    List<?> rows =
        entities
            .createNativeQuery(
                "SELECT p.tenant_id FROM catalog_product p JOIN store s ON s.id=p.store_id AND s.tenant_id=p.tenant_id WHERE s.slug=:slug AND s.status='PUBLISHED' AND p.id=:product AND p.published")
            .setParameter("slug", slug)
            .setParameter("product", product)
            .getResultList();
    if (rows.isEmpty()) throw missing();
    return (UUID) rows.getFirst();
  }

  private static ImageException missing() {
    return new ImageException(ImageException.Reason.MISSING, "Imagem ou produto indisponível.");
  }
}
