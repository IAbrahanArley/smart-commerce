package com.smartcommerce.catalog.services;

import com.smartcommerce.catalog.domain.BrandSummary;
import com.smartcommerce.catalog.domain.CategoryName;
import com.smartcommerce.catalog.domain.UrlSlug;
import com.smartcommerce.catalog.exceptions.BrandException;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import com.smartcommerce.catalog.repositories.BrandRepository;
import com.smartcommerce.catalog.repositories.CatalogRepository;
import com.smartcommerce.storefront.services.StorefrontChanges;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brands of a store (Sprint 8, SC-041, ADR-0018 V2). Only the store OWNER manages them; a brand of
 * another tenant is the same 404 as one that does not exist. Names follow the category rule (1 to
 * 60 characters, one line, unique in the store without regard to case).
 */
@Service
public class BrandService {
  /** Room for a multi-brand store without turning the storefront filter into a wall of names. */
  static final int MAX_BRANDS = 200;

  private final CatalogRepository catalog;
  private final BrandRepository brands;

  private final StorefrontChanges changes;

  public BrandService(
      CatalogRepository catalog, BrandRepository brands, StorefrontChanges changes) {
    this.changes = changes;
    this.catalog = catalog;
    this.brands = brands;
  }

  // Not read-only: the OWNER check locks the store (FOR SHARE), like CatalogService.
  @Transactional
  public List<BrandSummary> list(UUID actor, UUID tenant) {
    return owned(actor, tenant, store -> brands.list(tenant));
  }

  @Transactional
  public List<BrandSummary> create(UUID actor, UUID tenant, String rawName) {
    changes.changed(tenant);
    CategoryName name = CategoryName.parse(rawName);
    return owned(
        actor,
        tenant,
        store -> {
          if (brands.count(tenant) >= MAX_BRANDS)
            throw new BrandException(
                BrandException.Reason.LIMIT,
                "A loja já tem " + MAX_BRANDS + " marcas. Remova uma antes de criar outra.");
          UrlSlug slug =
              UrlSlug.from(name.name(), "marca", taken -> brands.slugTaken(tenant, taken));
          try {
            brands.create(
                UUID.randomUUID(),
                tenant,
                store,
                name.name(),
                name.key(),
                slug.value(),
                Instant.now());
          } catch (DuplicateKeyException duplicate) {
            throw nameConflict();
          }
          return brands.list(tenant);
        });
  }

  @Transactional
  public List<BrandSummary> rename(UUID actor, UUID tenant, UUID brand, String rawName) {
    changes.changed(tenant);
    CategoryName name = CategoryName.parse(rawName);
    return owned(
        actor,
        tenant,
        store -> {
          boolean renamed;
          try {
            renamed = brands.rename(tenant, brand, name.name(), name.key());
          } catch (DuplicateKeyException duplicate) {
            throw nameConflict();
          }
          if (!renamed) throw missing();
          return brands.list(tenant);
        });
  }

  /** Products of the brand stay in the catalog, without a brand. */
  @Transactional
  public List<BrandSummary> delete(UUID actor, UUID tenant, UUID brand) {
    changes.changed(tenant);
    return owned(
        actor,
        tenant,
        store -> {
          if (!brands.delete(tenant, brand)) throw missing();
          return brands.list(tenant);
        });
  }

  private <T> T owned(UUID actor, UUID tenant, Function<UUID, T> action) {
    UUID store;
    try {
      store = catalog.requireAccess(actor, tenant);
    } catch (CatalogPersistenceException absent) {
      throw missing();
    }
    return action.apply(store);
  }

  private static BrandException missing() {
    return new BrandException(BrandException.Reason.MISSING, "Marca ou loja não encontradas.");
  }

  private static BrandException nameConflict() {
    return new BrandException(
        BrandException.Reason.NAME_CONFLICT, "Já existe uma marca com esse nome na loja.");
  }
}
