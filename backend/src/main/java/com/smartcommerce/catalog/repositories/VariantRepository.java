package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.catalog.entities.ProductVariant;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class VariantRepository {
  private final EntityManager entities;

  public VariantRepository(EntityManager entities) {
    this.entities = entities;
  }

  public void lockProduct(UUID tenant, UUID product) {
    if (entities
        .createNativeQuery(
            "SELECT id FROM catalog_product WHERE id=:product AND tenant_id=:tenant FOR UPDATE")
        .setParameter("product", product)
        .setParameter("tenant", tenant)
        .getResultList()
        .isEmpty())
      throw new CatalogPersistenceException(CatalogPersistenceException.Reason.MISSING);
    entities.clear();
  }

  public List<ProductVariant> list(UUID tenant, UUID product) {
    return entities
        .createQuery(
            "SELECT v FROM ProductVariant v WHERE v.tenantId=:tenant AND v.productId=:product ORDER BY v.id",
            ProductVariant.class)
        .setParameter("tenant", tenant)
        .setParameter("product", product)
        .getResultList();
  }

  public Optional<ProductVariant> find(UUID tenant, UUID product, UUID id) {
    return entities
        .createQuery(
            "SELECT v FROM ProductVariant v WHERE v.tenantId=:tenant AND v.productId=:product AND v.id=:id",
            ProductVariant.class)
        .setParameter("tenant", tenant)
        .setParameter("product", product)
        .setParameter("id", id)
        .getResultStream()
        .findFirst();
  }

  public void create(UUID tenant, UUID product, UUID id, VariantValues input) {
    entities.persist(new ProductVariant(id, tenant, product, input));
    entities.flush();
  }

  public void update(UUID tenant, UUID product, UUID id, VariantValues input, long version) {
    int changed =
        entities
            .createNativeQuery(
                "UPDATE catalog_variant SET sku=:sku,price=:price,cost=:cost,version=version+1 WHERE tenant_id=:tenant AND product_id=:product AND id=:id AND version=:version")
            .setParameter("sku", input.values().sku())
            .setParameter("price", input.values().price())
            .setParameter("cost", input.values().cost())
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .setParameter("id", id)
            .setParameter("version", version)
            .executeUpdate();
    entities.clear();
    if (changed != 1)
      throw new CatalogPersistenceException(CatalogPersistenceException.Reason.VERSION_CONFLICT);
  }

  public void syncSimple(UUID tenant, UUID product, VariantValues input) {
    var current =
        find(tenant, product, product)
            .orElseThrow(
                () -> new CatalogPersistenceException(CatalogPersistenceException.Reason.MISSING));
    update(tenant, product, product, input, current.version());
  }
}
