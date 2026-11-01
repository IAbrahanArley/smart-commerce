package com.smartcommerce.storefront.services;

import com.smartcommerce.catalog.domain.ProductDescription;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.storefront.domain.PublicLocator;
import com.smartcommerce.storefront.domain.PublicProduct;
import com.smartcommerce.storefront.domain.PublicStore;
import com.smartcommerce.storefront.domain.StorefrontException;
import com.smartcommerce.storefront.dto.response.*;
import com.smartcommerce.storefront.repositories.StorefrontRepository;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorefrontService {
  private final StorefrontRepository storefront;
  private final PublicCatalogService catalog;
  private final Clock clock;

  public StorefrontService(
      StorefrontRepository storefront, PublicCatalogService catalog, Clock clock) {
    this.storefront = storefront;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicStorefrontView list(String slug, UUID after) {
    return list(slug, after, null);
  }

  /** {@code category} null lists every published product (MVP block 1c). */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicStorefrontView list(String slug, UUID after, UUID category) {
    var store = storefront.store(PublicLocator.slug(slug)).orElseThrow(StorefrontException::new);
    var products = storefront.list(slug, after, ReservationPolicy.now(clock), category);
    var page = products.stream().limit(20).map(StorefrontService::view).toList();
    var categories =
        storefront.categories(slug).stream()
            .map(row -> new PublicCategoryView((UUID) row[0], (String) row[1]))
            .toList();
    return new PublicStorefrontView(
        store.name(),
        store.slug(),
        profile(store),
        categories,
        category,
        page,
        products.size() > 20 ? page.getLast().id().toString() : null);
  }

  /** The product page, with its storefront details and related products (SC-042). */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicProductDetailsView find(String slug, UUID product) {
    return catalog.product(slug, product);
  }

  /** A product of the original list (ADR-0010), field for field. */
  private static PublicListProductView view(PublicProduct product) {
    return new PublicListProductView(
        product.id(),
        product.name(),
        product.price().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        product.currency(),
        product.available(),
        ProductDescription.sanitize(product.descriptionHtml()),
        product.kind(),
        List.of());
  }

  /** The published store's logo bytes and type (MVP block 1b). */
  @Transactional(readOnly = true)
  public StorefrontRepository.StoreLogo logo(String slug) {
    return storefront.logo(PublicLocator.slug(slug)).orElseThrow(StorefrontException::new);
  }

  private static PublicStoreProfileView profile(PublicStore store) {
    return new PublicStoreProfileView(
        store.contactEmail(),
        store.whatsapp(),
        store.instagram(),
        store.logo(),
        store.logoWidth(),
        store.logoHeight());
  }
}
