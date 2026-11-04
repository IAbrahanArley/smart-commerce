package com.smartcommerce.catalog.services;

import com.smartcommerce.billing.services.BillingService;
import com.smartcommerce.catalog.domain.*;
import com.smartcommerce.catalog.dto.response.*;
import com.smartcommerce.catalog.entities.*;
import com.smartcommerce.catalog.exceptions.*;
import com.smartcommerce.catalog.repositories.*;
import com.smartcommerce.storefront.services.StorefrontChanges;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final CatalogRepository products;
  private final VariantRepository variants;
  private final ProductImageRepository images;
  private final BillingService billing;
  private final ProductStorefrontRepository storefront;
  private final CategoryRepository categories;

  private final StorefrontChanges changes;

  public CatalogService(
      CatalogRepository products,
      VariantRepository variants,
      ProductImageRepository images,
      BillingService billing,
      ProductStorefrontRepository storefront,
      CategoryRepository categories,
      StorefrontChanges changes) {
    this.changes = changes;
    this.products = products;
    this.images = images;
    this.variants = variants;
    this.billing = billing;
    this.storefront = storefront;
    this.categories = categories;
  }

  @Transactional
  public void requireProductAccess(UUID actor, UUID tenant, UUID product) {
    execute(
        () -> {
          products.requireAccess(actor, tenant);
          owned(actor, tenant, product);
          return null;
        });
  }

  @Transactional
  public UUID requireStockAccess(UUID actor, UUID tenant, UUID product, UUID variant) {
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          var p = owned(actor, tenant, product);
          if (variant == null && p.kind().equals("VARIABLE"))
            throw outcome(
                CatalogException.Reason.VARIANT_REQUIRED,
                "Selecione uma variação para consultar o estoque.");
          UUID target = variant == null ? product : variant;
          variants.find(tenant, product, target).orElseThrow(CatalogService::missing);
          return target;
        });
  }

  @Transactional
  public ProductView create(UUID actor, UUID tenant, ProductValues input) {
    changes.changed(tenant);
    return create(actor, tenant, input, new ProductDetails("", ProductKind.SIMPLE, null));
  }

  @Transactional
  public ProductView create(UUID actor, UUID tenant, ProductValues input, ProductDetails details) {
    changes.changed(tenant);
    return execute(
        () -> {
          UUID store = products.requireAccess(actor, tenant);
          billing.requireProductSlot(tenant); // SC-031: products per store of the owner's plan
          var kind = details.kind() == null ? ProductKind.SIMPLE : details.kind();
          var inputs =
              kind == ProductKind.SIMPLE
                  ? List.of(new VariantValues(input, List.of()))
                  : details.variants();
          if (kind == ProductKind.SIMPLE
              && details.variants() != null
              && !details.variants().isEmpty())
            throw new IllegalArgumentException("Simple product has one SKU");
          validateVariants(inputs, kind);
          var p =
              products.create(
                  actor,
                  tenant,
                  store,
                  UUID.randomUUID(),
                  input,
                  Instant.now(),
                  new ProductDetails(
                      ProductDescription.sanitize(details.descriptionHtml()), kind, null),
                  // SC-041: the storefront address comes from the name; editable later.
                  UrlSlug.from(
                          input.name(),
                          "produto",
                          taken -> storefront.slugTaken(tenant, taken, null))
                      .value());
          for (var value : inputs)
            variants.create(
                tenant, p.id(), kind == ProductKind.SIMPLE ? p.id() : UUID.randomUUID(), value);
          return view(p);
        });
  }

  /** One page of the admin product list, filtered, ordered and counted in the database. */
  @Transactional
  public ProductPageView page(UUID actor, UUID tenant, ProductListQuery query) {
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          var page = products.page(actor, tenant, query);
          var counts = page.counts();
          return new ProductPageView(
              views(tenant, page.products()),
              query.page(),
              ProductListQuery.PAGE_SIZE,
              counts.matching(),
              counts.catalog(),
              counts.published());
        });
  }

  private List<ProductView> views(UUID tenant, List<Product> list) {
    var ids = list.stream().map(Product::id).toList();
    var summaries = images.summaries(tenant, ids);
    var additional = categories.additional(tenant, ids);
    return list.stream()
        .map(
            p ->
                view(
                    p,
                    summaries.getOrDefault(p.id(), ProductImageSummary.NONE),
                    additional.getOrDefault(p.id(), List.of())))
        .toList();
  }

  @Transactional
  public ProductView find(UUID actor, UUID tenant, UUID product) {
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          return view(owned(actor, tenant, product));
        });
  }

  @Transactional
  public ProductView update(
      UUID actor, UUID tenant, UUID product, ProductValues input, long version) {
    changes.changed(tenant);
    return update(actor, tenant, product, input, version, new ProductDetails(null, null, null));
  }

  @Transactional
  public ProductView update(
      UUID actor,
      UUID tenant,
      UUID product,
      ProductValues input,
      long version,
      ProductDetails details) {
    changes.changed(tenant);
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          var current = owned(actor, tenant, product);
          checkVersion(version);
          if (details.kind() != null && !current.kind().equals(details.kind().name()))
            throw outcome(
                CatalogException.Reason.PRODUCT_KIND_CONFLICT,
                "O tipo do produto não pode ser alterado após a criação.");
          if (details.variants() != null)
            throw new IllegalArgumentException("Use the variant operations");
          boolean simple = current.kind().equals("SIMPLE");
          var values =
              simple
                  ? input
                  : ProductValues.parse(
                      input.name(),
                      current.sku(),
                      money(current.price()),
                      current.cost() == null ? null : money(current.cost()));
          var updated =
              products.update(
                  actor,
                  tenant,
                  product,
                  values,
                  version,
                  Instant.now(),
                  details.descriptionHtml() == null
                      ? null
                      : ProductDescription.sanitize(details.descriptionHtml()));
          if (simple) variants.syncSimple(tenant, product, new VariantValues(values, List.of()));
          return view(updated);
        });
  }

  @Transactional
  public ProductView addVariant(
      UUID actor, UUID tenant, UUID product, VariantValues input, long version) {
    changes.changed(tenant);
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          owned(actor, tenant, product);
          variants.lockProduct(tenant, product);
          var p = owned(actor, tenant, product);
          requireVariable(p);
          checkVersion(version);
          if (p.version() != version)
            throw new CatalogPersistenceException(
                CatalogPersistenceException.Reason.VERSION_CONFLICT);
          var existing = variants.list(tenant, product);
          if (existing.size() >= 50)
            throw outcome(
                CatalogException.Reason.VARIANT_LIMIT, "O produto permite até 50 variações.");
          var current =
              existing.stream()
                  .map(
                      v ->
                          new VariantValues(
                              ProductValues.parse(
                                  p.name(),
                                  v.sku(),
                                  money(v.price()),
                                  v.cost() == null ? null : money(v.cost())),
                              VariantValues.decode(v.optionsJson())))
                  .toList();
          var all = new ArrayList<>(current);
          all.add(input);
          validateVariants(all, ProductKind.VARIABLE);
          variants.create(tenant, product, UUID.randomUUID(), input);
          return view(touch(actor, tenant, p));
        });
  }

  @Transactional
  public ProductView updateVariant(
      UUID actor, UUID tenant, UUID product, UUID variant, VariantValues input, long version) {
    changes.changed(tenant);
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          owned(actor, tenant, product);
          variants.lockProduct(tenant, product);
          var p = owned(actor, tenant, product);
          requireVariable(p);
          checkVersion(version);
          var current =
              variants.find(tenant, product, variant).orElseThrow(CatalogService::missing);
          if (!input.options().isEmpty()
              && !input.options().equals(VariantValues.decode(current.optionsJson())))
            throw new IllegalArgumentException("Options are immutable");
          variants.update(tenant, product, variant, input, version);
          return view(touch(actor, tenant, p));
        });
  }

  @Transactional
  public ProductView publish(
      UUID actor, UUID tenant, UUID product, boolean published, long version) {
    changes.changed(tenant);
    return execute(
        () -> {
          products.requireAccess(actor, tenant);
          owned(actor, tenant, product);
          checkVersion(version);
          return view(products.publish(actor, tenant, product, published, version, Instant.now()));
        });
  }

  private Product touch(UUID actor, UUID tenant, Product p) {
    var rows = variants.list(tenant, p.id());
    var first = rows.getFirst();
    BigDecimal price =
        rows.stream().map(ProductVariant::price).min(BigDecimal::compareTo).orElseThrow();
    return products.update(
        actor,
        tenant,
        p.id(),
        ProductValues.parse(
            p.name(), first.sku(), money(price), first.cost() == null ? null : money(first.cost())),
        p.version(),
        Instant.now(),
        null);
  }

  private Product owned(UUID actor, UUID tenant, UUID product) {
    return products.find(actor, tenant, product).orElseThrow(CatalogService::missing);
  }

  private void requireVariable(Product p) {
    if (!p.kind().equals("VARIABLE"))
      throw outcome(
          CatalogException.Reason.PRODUCT_KIND_CONFLICT,
          "Edite o SKU do produto simples no formulário do produto.");
  }

  private static void validateVariants(List<VariantValues> values, ProductKind kind) {
    if (values == null || values.isEmpty() || values.size() > 50)
      throw new IllegalArgumentException("Expected 1 to 50 variants");
    var names = values.getFirst().names();
    if (kind == ProductKind.VARIABLE && names.isEmpty())
      throw new IllegalArgumentException("Options required");
    Set<String> keys = new HashSet<>(), skus = new HashSet<>();
    for (var v : values) {
      if (!v.names().equals(names)) throw new IllegalArgumentException("Option names differ");
      if (!keys.add(v.key()))
        throw outcome(
            CatalogException.Reason.COMBINATION_CONFLICT, "Esta combinação já existe no produto.");
      if (!skus.add(v.values().sku()))
        throw outcome(
            CatalogException.Reason.SKU_CONFLICT, "Já existe um produto com esse SKU nesta loja.");
    }
  }

  private ProductView view(Product p) {
    return view(
        p,
        images
            .summaries(p.tenantId(), List.of(p.id()))
            .getOrDefault(p.id(), ProductImageSummary.NONE),
        categories.additional(p.tenantId(), List.of(p.id())).getOrDefault(p.id(), List.of()));
  }

  private ProductView view(Product p, ProductImageSummary photos, List<UUID> additional) {
    var rows = variants.list(p.tenantId(), p.id());
    return new ProductView(
        p.id(),
        p.name(),
        p.sku(),
        money(
            rows.stream().map(ProductVariant::price).min(BigDecimal::compareTo).orElse(p.price())),
        p.cost() == null ? null : money(p.cost()),
        p.currency(),
        p.version(),
        p.published(),
        ProductDescription.sanitize(p.descriptionHtml()),
        p.kind(),
        rows.stream()
            .map(
                v ->
                    new VariantView(
                        v.id(),
                        v.sku(),
                        money(v.price()),
                        v.cost() == null ? null : money(v.cost()),
                        v.currency(),
                        v.version(),
                        VariantValues.decode(v.optionsJson()),
                        v.compareAtPrice() == null ? null : money(v.compareAtPrice())))
            .toList(),
        photos.coverId(),
        photos.coverAlt(),
        photos.count(),
        p.categoryId(),
        p.brandId(),
        p.featured(),
        p.slug(),
        additional);
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static void checkVersion(long version) {
    if (version < 0 || version == Long.MAX_VALUE)
      throw new IllegalArgumentException("Invalid version");
  }

  private static CatalogException outcome(CatalogException.Reason reason, String message) {
    return new CatalogException(reason, message);
  }

  private static <T> T execute(Supplier<T> work) {
    try {
      return work.get();
    } catch (CatalogPersistenceException e) {
      throw switch (e.reason()) {
        case MISSING -> missing();
        case VERSION_CONFLICT ->
            outcome(
                CatalogException.Reason.VERSION_CONFLICT,
                "O produto foi alterado. Atualize a página antes de salvar novamente.");
      };
    } catch (DataIntegrityViolationException e) {
      throw outcome(
          CatalogException.Reason.SKU_CONFLICT, "Já existe um produto com esse SKU nesta loja.");
    }
  }

  static CatalogException missing() {
    return outcome(CatalogException.Reason.MISSING, "Produto ou loja não encontrado.");
  }
}
