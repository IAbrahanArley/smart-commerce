package com.smartcommerce.catalog.domain;

import java.util.List;

public record ProductDetails(
    String descriptionHtml, ProductKind kind, List<VariantValues> variants) {}
