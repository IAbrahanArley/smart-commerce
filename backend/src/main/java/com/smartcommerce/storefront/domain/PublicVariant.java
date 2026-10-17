package com.smartcommerce.storefront.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicVariant(
    UUID id, BigDecimal price, String currency, boolean available, String optionsJson) {}
