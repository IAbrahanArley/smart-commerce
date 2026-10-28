package com.smartcommerce.catalog.services;

import com.smartcommerce.catalog.domain.CategoryName;
import com.smartcommerce.catalog.domain.CategorySummary;
import com.smartcommerce.catalog.domain.CategoryTree;
import com.smartcommerce.catalog.domain.UrlSlug;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import com.smartcommerce.catalog.exceptions.CategoryException;
import com.smartcommerce.catalog.repositories.CatalogRepository;
import com.smartcommerce.catalog.repositories.CategoryRepository;
import com.smartcommerce.storefront.services.StorefrontChanges;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product categories of a store (MVP block 1c, PRD item 3), arranged since Sprint 8 (SC-041,
 * ADR-0018 V1) in a tree of up to 3 levels. Only the store OWNER manages them; every operation
 * first locks the store through the OWNER's membership, so a category or product of another tenant
 * is the same 404 as one that does not exist. Changes to the tree also lock every category row of
 * the tenant, so concurrent moves cannot build a cycle or go deeper than allowed.
 */
@Service
public class CategoryService {
  /** Enough for a small store's catalog; keeps the storefront navigation short. */
  static final int MAX_CATEGORIES = 50;

  private final CatalogRepository catalog;
  private final CategoryRepository categories;
  private final CatalogService products;

  private final StorefrontChanges changes;

  public CategoryService(
      CatalogRepository catalog,
      CategoryRepository categories,
      CatalogService products,
      StorefrontChanges changes) {
    this.changes = changes;
    this.catalog = catalog;
    this.categories = categories;
    this.products = products;
  }

  // Not read-only: the OWNER check locks the store (FOR SHARE), like CatalogService.
  @Transactional
  public List<CategorySummary> list(UUID actor, UUID tenant) {
    return owned(actor, tenant, () -> summaries(tenant));
  }

  /** Creates a root ({@code parent} null) or a subcategory, last among its siblings. */
  @Transactional
  public List<CategorySummary> create(UUID actor, UUID tenant, String rawName, UUID parent) {
    changes.changed(tenant);
    CategoryName name = CategoryName.parse(rawName);
    return owned(
        actor,
        tenant,
        () -> {
          CategoryTree tree = categories.lockTree(tenant);
          if (tree.size() >= MAX_CATEGORIES)
            throw new CategoryException(
                CategoryException.Reason.LIMIT,
                "A loja já tem "
                    + MAX_CATEGORIES
                    + " categorias. Remova uma antes de criar outra.");
          if (parent != null) {
            if (!tree.contains(parent)) throw missing();
            if (tree.depth(parent) >= CategoryTree.MAX_DEPTH) throw tooDeep();
          }
          UrlSlug slug =
              UrlSlug.from(
                  name.name(), "categoria", taken -> categories.slugTaken(tenant, taken, null));
          try {
            categories.create(
                UUID.randomUUID(),
                tenant,
                catalog.requireAccess(actor, tenant),
                parent,
                name.name(),
                name.key(),
                slug.value(),
                tree.children(parent).size(),
                Instant.now());
          } catch (DuplicateKeyException duplicate) {
            throw nameConflict();
          }
          return summaries(tenant);
        });
  }

  @Transactional
  public List<CategorySummary> rename(UUID actor, UUID tenant, UUID category, String rawName) {
    changes.changed(tenant);
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
          return summaries(tenant);
        });
  }

  /** Changes the storefront address of the category; renaming never changes it by itself. */
  @Transactional
  public List<CategorySummary> changeSlug(UUID actor, UUID tenant, UUID category, String rawSlug) {
    changes.changed(tenant);
    UrlSlug slug = UrlSlug.parse(rawSlug);
    return owned(
        actor,
        tenant,
        () -> {
          boolean changed;
          try {
            changed = categories.changeSlug(tenant, category, slug.value());
          } catch (DuplicateKeyException duplicate) {
            throw new CategoryException(
                CategoryException.Reason.SLUG_CONFLICT,
                "Outra categoria da loja já usa esse endereço.");
          }
          if (!changed) throw missing();
          return summaries(tenant);
        });
  }

  /** Hides or shows the category in the storefront; its products are not affected. */
  @Transactional
  public List<CategorySummary> publish(UUID actor, UUID tenant, UUID category, boolean published) {
    changes.changed(tenant);
    return owned(
        actor,
        tenant,
        () -> {
          if (!categories.publish(tenant, category, published)) throw missing();
          return summaries(tenant);
        });
  }

  /**
   * Places the category under {@code parent} ({@code null}: root) at {@code position} among its new
   * siblings, keeping the whole subtree within {@link CategoryTree#MAX_DEPTH} levels.
   */
  @Transactional
  public List<CategorySummary> place(
      UUID actor, UUID tenant, UUID category, UUID parent, int position) {
    changes.changed(tenant);
    return owned(
        actor,
        tenant,
        () -> {
          CategoryTree tree = categories.lockTree(tenant);
          if (!tree.contains(category) || (parent != null && !tree.contains(parent)))
            throw missing();
          if (parent != null && tree.isWithin(parent, category))
            throw new CategoryException(
                CategoryException.Reason.CYCLE,
                "Uma categoria não pode ficar dentro dela mesma ou de uma subcategoria sua.");
          int parentDepth = parent == null ? 0 : tree.depth(parent);
          if (parentDepth + tree.height(category) > CategoryTree.MAX_DEPTH) throw tooDeep();
          UUID previousParent = tree.parent(category);
          try {
            if (!Objects.equals(previousParent, parent)) categories.move(tenant, category, parent);
          } catch (DuplicateKeyException duplicate) {
            throw nameConflict();
          }
          categories.reorder(tenant, tree.placed(category, parent, position));
          if (!Objects.equals(previousParent, parent))
            categories.reorder(
                tenant,
                tree.children(previousParent).stream()
                    .map(CategoryTree.Node::id)
                    .filter(id -> !id.equals(category))
                    .toList());
          return summaries(tenant);
        });
  }

  /**
   * Products of the category stay in the catalog, without a category. A category with subcategories
   * is kept: they would otherwise be left without a place in the tree.
   */
  @Transactional
  public List<CategorySummary> delete(UUID actor, UUID tenant, UUID category) {
    changes.changed(tenant);
    return owned(
        actor,
        tenant,
        () -> {
          CategoryTree tree = categories.lockTree(tenant);
          if (!tree.contains(category)) throw missing();
          if (tree.hasChildren(category))
            throw new CategoryException(
                CategoryException.Reason.HAS_CHILDREN,
                "Mova ou remova as subcategorias antes de remover esta categoria.");
          categories.delete(tenant, category);
          return summaries(tenant);
        });
  }

  /**
   * Replaces the categories the product also appears in (SC-043). The primary category is left out
   * of the list if sent; a category of another tenant is the same 404 as none.
   */
  @Transactional
  public ProductView assignAdditional(UUID actor, UUID tenant, UUID product, List<UUID> wanted) {
    changes.changed(tenant);
    owned(
        actor,
        tenant,
        () -> {
          if (!categories.lockProduct(tenant, product)) throw missing();
          return null;
        });
    UUID primary = products.find(actor, tenant, product).categoryId();
    try {
      categories.replaceAdditional(
          tenant, product, wanted.stream().filter(id -> !id.equals(primary)).toList());
    } catch (DataIntegrityViolationException foreign) {
      throw missing();
    }
    return products.find(actor, tenant, product);
  }

  /** Sets ({@code category} present) or clears the category of one product. */
  @Transactional
  public ProductView assign(UUID actor, UUID tenant, UUID product, UUID category) {
    changes.changed(tenant);
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

  /** Every category, parents before children, siblings by position. */
  private List<CategorySummary> summaries(UUID tenant) {
    Map<UUID, CategoryRepository.Row> rows = categories.rows(tenant);
    CategoryTree tree =
        new CategoryTree(
            rows.values().stream()
                .map(
                    row ->
                        new CategoryTree.Node(
                            row.id(),
                            row.parent(),
                            row.position(),
                            row.name().toLowerCase(Locale.ROOT)))
                .toList());
    return tree.depthFirst().stream()
        .map(
            id -> {
              CategoryRepository.Row row = rows.get(id);
              return new CategorySummary(
                  row.id(),
                  row.name(),
                  row.slug(),
                  row.parent(),
                  tree.depth(id),
                  row.position(),
                  row.published(),
                  row.products());
            })
        .toList();
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

  private static CategoryException tooDeep() {
    return new CategoryException(
        CategoryException.Reason.DEPTH,
        "As categorias podem ter no máximo " + CategoryTree.MAX_DEPTH + " níveis.");
  }

  private static CategoryException nameConflict() {
    return new CategoryException(
        CategoryException.Reason.NAME_CONFLICT,
        "Já existe uma categoria com esse nome no mesmo nível.");
  }
}
