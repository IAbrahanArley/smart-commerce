package com.smartcommerce.storefront.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A product of the original paginated list (ADR-0010). Kept field for field, so that contract does
 * not change; the SC-042 catalog has its own cards ({@link PublicCatalogView.Card}).
 */
public record PublicListProductView(
    UUID id,
    String name,
    String price,
    String currency,
    boolean available,
    String descriptionHtml,
    String kind,
    List<PublicVariantView> variants) {}
