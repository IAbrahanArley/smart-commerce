package com.smartcommerce.catalog.repositories;

import com.smartcommerce.catalog.domain.ProductDetails;
import com.smartcommerce.catalog.domain.ProductValues;
import com.smartcommerce.catalog.entities.Product;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException.Reason;
import com.smartcommerce.tenant.repositories.TenantAccessRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
  private final ProductJpaRepository products;
  private final TenantAccessRepository access;
  private final EntityManager entities;

  public CatalogRepository(
      ProductJpaRepository products, TenantAccessRepository access, EntityManager entities) {
    this.products = products;
    this.access = access;
    this.entities = entities;
  }

  public UUID requireAccess(UUID actor, UUID tenant) {
    return access
        .lockOwnedStore(actor, tenant)
        .orElseThrow(() -> new CatalogPersistenceException(Reason.MISSING));
  }

  public List<Product> list(UUID actor, UUID tenant) {
    return products.listOwned(actor, tenant);
  }

  public Optional<Product> find(UUID actor, UUID tenant, UUID product) {
    return products.findOwned(actor, tenant, product);
  }

  public Product create(
      UUID actor, UUID tenant, UUID store, UUID id, ProductValues input, Instant now) {
    return create(actor, tenant, store, id, input, now, new ProductDetails("", null, null), null);
  }

  public Product create(
      UUID actor,
      UUID tenant,
      UUID store,
      UUID id,
      ProductValues input,
      Instant now,
      ProductDetails details,
      String slug) {
    UUID authorizedStore = requireAccess(actor, tenant);
    if (!authorizedStore.equals(store)) throw new CatalogPersistenceException(Reason.MISSING);
    Product product =
        new Product(
            id,
            tenant,
            store,
            input.name(),
            input.sku(),
            input.price(),
            input.cost(),
            "BRL",
            0,
            now,
            now);
    product.initializeDetails(
        details.descriptionHtml() == null ? "" : details.descriptionHtml(),
        details.kind() == null ? "SIMPLE" : details.kind().name());
    product.initializeSlug(slug);
    entities.persist(product);
    entities.flush();
    return product;
  }

  public Product update(
      UUID actor, UUID tenant, UUID id, ProductValues input, long version, Instant now) {
    return update(actor, tenant, id, input, version, now, null);
  }

  public Product update(
      UUID actor,
      UUID tenant,
      UUID id,
      ProductValues input,
      long version,
      Instant now,
      String description) {
    requireAccess(actor, tenant);
    if (products.updateOwned(
            actor,
            tenant,
            id,
            input.name(),
            input.sku(),
            input.price(),
            input.cost(),
            version,
            now,
            description)
        != 1) {
      if (find(actor, tenant, id).isEmpty()) throw new CatalogPersistenceException(Reason.MISSING);
      throw new CatalogPersistenceException(Reason.VERSION_CONFLICT);
    }
    return find(actor, tenant, id)
        .orElseThrow(() -> new CatalogPersistenceException(Reason.MISSING));
  }

  public Product publish(
      UUID actor, UUID tenant, UUID product, boolean published, long version, Instant now) {
    requireAccess(actor, tenant);
    if (products.publishOwned(actor, tenant, product, published, version, now) != 1) {
      if (find(actor, tenant, product).isEmpty())
        throw new CatalogPersistenceException(Reason.MISSING);
      throw new CatalogPersistenceException(Reason.VERSION_CONFLICT);
    }
    return find(actor, tenant, product)
        .orElseThrow(() -> new CatalogPersistenceException(Reason.MISSING));
  }
}
