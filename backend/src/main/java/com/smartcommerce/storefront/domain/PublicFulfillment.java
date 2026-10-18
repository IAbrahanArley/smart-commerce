package com.smartcommerce.storefront.domain;

import java.math.BigDecimal;

/**
 * Enabled delivery options of a published store; null fields mean the option is disabled. The
 * schema guarantees enabled pickup has instructions and enabled shipping has amount and area.
 */
public record PublicFulfillment(
    String pickupInstructions, BigDecimal shippingAmount, String currency, String shippingArea) {}
