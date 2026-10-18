package com.smartcommerce.tenant.dto.response;

/** Owner representation; the decimal string preserves the BRL contract without floats. */
public record FulfillmentView(
    boolean pickupEnabled,
    String pickupInstructions,
    boolean fixedShippingEnabled,
    String fixedShippingAmount,
    String currency,
    String fixedShippingArea,
    long version) {}
