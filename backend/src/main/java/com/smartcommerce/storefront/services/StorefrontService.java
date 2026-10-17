package com.smartcommerce.storefront.services;

import com.smartcommerce.catalog.domain.ProductDescription;
import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.storefront.domain.PublicLocator;
import com.smartcommerce.storefront.domain.PublicProduct;
import com.smartcommerce.storefront.domain.StorefrontException;
import com.smartcommerce.storefront.dto.response.*;
import com.smartcommerce.storefront.repositories.StorefrontRepository;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorefrontService {
  private final StorefrontRepository storefront;

  public StorefrontService(StorefrontRepository storefront) {
    this.storefront = storefront;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicStorefrontView list(String slug, UUID after) {
    var store = storefront.store(PublicLocator.slug(slug)).orElseThrow(StorefrontException::new);
    var products = storefront.list(slug, after);
    var page = products.stream().limit(20).map(StorefrontService::view).toList();
    return new PublicStorefrontView(
        store.name(),
        store.slug(),
        page,
        products.size() > 20 ? page.getLast().id().toString() : null);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PublicProductDetailsView find(String slug, UUID product) {
    var store = storefront.store(PublicLocator.slug(slug)).orElseThrow(StorefrontException::new);
    var found = storefront.find(slug, product).orElseThrow(StorefrontException::new);
    return new PublicProductDetailsView(
        new PublicStoreView(store.name(), store.slug()),
        view(
            found,
            storefront.variants(slug, product).stream()
                .map(
                    v ->
                        new PublicVariantView(
                            v.id(),
                            v.price().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                            v.currency(),
                            v.available(),
                            VariantValues.decode(v.optionsJson())))
                .toList()));
  }

  private static PublicProductView view(PublicProduct product) {
    return view(product, List.of());
  }

  private static PublicProductView view(PublicProduct product, List<PublicVariantView> variants) {
    return new PublicProductView(
        product.id(),
        product.name(),
        product.price().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
        product.currency(),
        product.available(),
        ProductDescription.sanitize(product.descriptionHtml()),
        product.kind(),
        variants);
  }
}
