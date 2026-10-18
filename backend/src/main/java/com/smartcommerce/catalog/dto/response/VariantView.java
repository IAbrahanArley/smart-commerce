package com.smartcommerce.catalog.dto.response;

import com.smartcommerce.catalog.domain.VariantOption;
import java.util.List;
import java.util.UUID;

public record VariantView(
    UUID id,
    String sku,
    String price,
    String cost,
    String currency,
    long version,
    List<VariantOption> options) {}
