package com.smartcommerce.inventory.domain;

import java.time.Instant;
import java.util.UUID;

/** A variant in a dashboard stock list (SC-022): available units and last sale, if any. */
public record StockInsight(
    UUID variantId,
    UUID productId,
    String productName,
    String optionsJson,
    int available,
    Instant lastSaleAt) {}
