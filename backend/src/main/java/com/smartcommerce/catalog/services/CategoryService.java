package com.smartcommerce.catalog.services;

import com.smartcommerce.catalog.domain.CategoryName;
import com.smartcommerce.catalog.domain.CategorySummary;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import com.smartcommerce.catalog.exceptions.CategoryException;
import com.smartcommerce.catalog.repositories.CatalogRepository;
import com.smartcommerce.catalog.repositories.CategoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flat product categories of a store (MVP block 1c, PRD item 3). Only the store OWNER manages them;
 * every operation first locks the store through the OWNER's membership, so a category or product of
 * another tenant is the same 404 as one that does not exist.
 */
@Service
public class CategoryService {
  /** Enough for a small store's catalog; keeps the storefront navigation short. */
  static final int MAX_CATEGORIES = 50;

  private final CatalogRepository catalog;
  private final CategoryRepository categories;
  private final CatalogService products;

  public CategoryService(
      CatalogRepository catalog, CategoryRepository categories, CatalogService products) {
    this.catalog = catalog;
    this.categories = categories;
    this.products = products;
  }

  // Not read-only: the OWNER check locks the store (FOR SHARE), like CatalogService.
  @Transactional
  public List<CategorySummary> list(UUID actor, UUID tenant) {
    return owned(actor, tenant, () -> categories.list(tenant));
  }

  @Transactional
  public List<CategorySummary> create(UUID actor, UUID tenant, String rawName) {
    CategoryName name = CategoryName.parse(rawName);
    return owned(
        actor,
        tenant,
        () -> {
          if (categories.count(tenant) >= MAX_CATEGORIES)
            throw new CategoryException(
                CategoryException.Reason.LIMIT,
                "A loja já tem "
                    + MAX_CATEGORIES
                    + " categorias. Remova uma antes de criar outra.");
          try {
            categories.create(
                UUID.randomUUID(),
                tenant,
                catalog.requireAccess(actor, tenant),
                name.name(),
                name.key(),
                Instant.now());
          } catch (DuplicateKeyException duplicate) {
            throw nameConflict();
          }
          return categories.list(tenant);
        });
  }

  @Transactional
  public List<CategorySummary> rename(UUID actor, UUID tenant, UUID category, String rawName) {
    CategoryName name = CategoryName.parse(rawName);
    return owned(
        actor,
        tenant,
        () -> {
          boolean renamed;
          try {
            renamed = categories.rename(tenant, category, name.name(), name.key());
          } catch (DuplicateKeyException duplicate) {
            throw nameConflict();
          }
          if (!renamed) throw missing();
          return categories.list(tenant);
        });
  }

  /** Products of the category stay in the catalog, without a category. */
  @Transactional
  public List<CategorySummary> delete(UUID actor, UUID tenant, UUID category) {
    return owned(
        actor,
        tenant,
        () -> {
          if (!categories.delete(tenant, category)) throw missing();
          return categories.list(tenant);
        });
  }

  /** Sets ({@code category} present) or clears the category of one product. */
  @Transactional
  public ProductView assign(UUID actor, UUID tenant, UUID product, UUID category) {
    owned(
        actor,
        tenant,
        () -> {
          boolean assigned;
          try {
            assigned = categories.assign(tenant, product, category);
          } catch (DataIntegrityViolationException foreign) {
            throw missing(); // a category of another tenant is the same as none
          }
          if (!assigned) throw missing();
          return null;
        });
    return products.find(actor, tenant, product);
  }

  private <T> T owned(UUID actor, UUID tenant, Supplier<T> action) {
    try {
      catalog.requireAccess(actor, tenant);
    } catch (CatalogPersistenceException absent) {
      throw missing();
    }
    return action.get();
  }

  private static CategoryException missing() {
    return new CategoryException(
        CategoryException.Reason.MISSING, "Categoria, produto ou loja não encontrados.");
  }

  private static CategoryException nameConflict() {
    return new CategoryException(
        CategoryException.Reason.NAME_CONFLICT, "Já existe uma categoria com esse nome na loja.");
  }
}
