package com.smartcommerce.catalog.services;

import com.smartcommerce.catalog.domain.UrlSlug;
import com.smartcommerce.catalog.dto.request.ProductStorefrontRequest;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.exceptions.CatalogPersistenceException;
import com.smartcommerce.catalog.exceptions.ProductStorefrontException;
import com.smartcommerce.catalog.repositories.CatalogRepository;
import com.smartcommerce.catalog.repositories.ProductStorefrontRepository;
import com.smartcommerce.storefront.services.StorefrontChanges;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "Vitrine" card of a product (Sprint 8, SC-041, ADR-0018 V2/V3/V5): brand, storefront address
 * and "compare at" prices, saved together and apart from the product form, so they never conflict
 * with its version. Only the store OWNER; another tenant's product or brand is a 404.
 */
@Service
public class ProductStorefrontService {
  private final CatalogRepository catalog;
  private final ProductStorefrontRepository storefront;
  private final CatalogService products;

  private final StorefrontChanges changes;

  public ProductStorefrontService(
      CatalogRepository catalog,
      ProductStorefrontRepository storefront,
      CatalogService products,
      StorefrontChanges changes) {
    this.changes = changes;
    this.catalog = catalog;
    this.storefront = storefront;
    this.products = products;
  }

  @Transactional
  public ProductView update(
      UUID actor, UUID tenant, UUID product, ProductStorefrontRequest request) {
    changes.changed(tenant);
    UrlSlug slug = request.slug() == null ? null : UrlSlug.parse(request.slug());
    Map<UUID, BigDecimal> compareAt = new LinkedHashMap<>();
    for (var variant : request.variantList()) compareAt.put(variant.id(), variant.value());
    try {
      catalog.requireAccess(actor, tenant);
    } catch (CatalogPersistenceException absent) {
      throw missing();
    }
    ProductStorefrontRepository.Locked locked =
        storefront.lock(tenant, product).orElseThrow(ProductStorefrontService::missing);

    Map<UUID, BigDecimal> prices = storefront.variantPrices(tenant, product);
    for (var entry : compareAt.entrySet()) {
      BigDecimal price = prices.get(entry.getKey());
      if (price == null) throw missing();
      if (entry.getValue() != null && entry.getValue().compareTo(price) <= 0)
        throw new ProductStorefrontException(
            ProductStorefrontException.Reason.COMPARE_AT_NOT_ABOVE,
            "O preço “de” precisa ser maior que o preço atual.");
    }

    if (slug != null && !slug.value().equals(locked.slug())) {
      if (storefront.slugTaken(tenant, slug.value(), product)) throw slugConflict();
      try {
        storefront.changeSlug(
            tenant, locked.store(), product, locked.slug(), slug.value(), Instant.now());
      } catch (DuplicateKeyException duplicate) {
        throw slugConflict();
      }
    }
    try {
      storefront.assignBrand(tenant, product, request.brandId());
    } catch (DataIntegrityViolationException foreign) {
      throw missing(); // a brand of another tenant is the same as none
    }
    if (request.featured() != null) storefront.feature(tenant, product, request.featured());
    compareAt.forEach((variant, value) -> storefront.compareAt(tenant, product, variant, value));
    return products.find(actor, tenant, product);
  }

  private static ProductStorefrontException missing() {
    return new ProductStorefrontException(
        ProductStorefrontException.Reason.MISSING, "Produto, marca ou loja não encontrados.");
  }

  private static ProductStorefrontException slugConflict() {
    return new ProductStorefrontException(
        ProductStorefrontException.Reason.SLUG_CONFLICT,
        "Outro produto da loja já usa esse endereço.");
  }
}
