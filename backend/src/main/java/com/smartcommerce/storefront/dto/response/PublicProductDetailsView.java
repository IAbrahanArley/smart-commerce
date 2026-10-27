package com.smartcommerce.storefront.dto.response;

import java.util.List;

/** A published product with up to 8 related products of the same category (SC-042). */
public record PublicProductDetailsView(
    PublicStoreView store,
    PublicProductView product,
    List<PublicCatalogView.Card> related,
    // SC-042: as in the catalog, null when the store promises no installments.
    Integer maxInstallments) {}
