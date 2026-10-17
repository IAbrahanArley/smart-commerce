package com.smartcommerce.storefront.dto.response;

import com.smartcommerce.catalog.domain.VariantOption;
import java.util.List;
import java.util.UUID;

/** Unavailable lines echo only the selector, quantity and status, whatever the cause. */
public record CartQuoteLineView(
    UUID variantId,
    int quantity,
    String status,
    UUID productId,
    String productName,
    List<VariantOption> options,
    String unitPrice,
    String lineTotal) {}
