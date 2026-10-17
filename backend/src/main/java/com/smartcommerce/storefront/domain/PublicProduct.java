package com.smartcommerce.storefront.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicProduct(
    UUID id,
    String name,
    BigDecimal price,
    String currency,
    boolean available,
    String descriptionHtml,
    String kind) {}
