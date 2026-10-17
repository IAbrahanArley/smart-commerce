package com.smartcommerce.storefront.dto.response;

import com.smartcommerce.catalog.domain.VariantOption;
import java.util.List;
import java.util.UUID;

public record PublicVariantView(
    UUID id, String price, String currency, boolean available, List<VariantOption> options) {}
