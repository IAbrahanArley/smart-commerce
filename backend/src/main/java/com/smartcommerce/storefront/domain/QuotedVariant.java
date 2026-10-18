package com.smartcommerce.storefront.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Internal quote projection; {@code sellable} decides the line state and is never exposed. */
public record QuotedVariant(
    UUID id,
    UUID productId,
    String productName,
    BigDecimal price,
    String currency,
    String optionsJson,
    long sellable) {}
