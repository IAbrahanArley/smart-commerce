package com.smartcommerce.storefront.dto.response;

import java.util.List;
import java.util.UUID;

public record PublicProductView(
    UUID id,
    String name,
    String price,
    String currency,
    boolean available,
    String descriptionHtml,
    String kind,
    List<PublicVariantView> variants,
    // SC-042: storefront details of the product page.
    String slug,
    PublicCatalogView.Link brand,
    List<PublicCatalogView.Link> categoryPath,
    String compareAtPrice,
    Integer discountPercent) {}
