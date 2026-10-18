package com.smartcommerce.tenant.dto.request;

import com.smartcommerce.tenant.domain.FulfillmentSettings;
import tools.jackson.databind.JsonNode;

/** Strict JSON types: booleans, decimal strings for money and an integral version. */
public record FulfillmentRequest(
    JsonNode pickupEnabled,
    JsonNode pickupInstructions,
    JsonNode fixedShippingEnabled,
    JsonNode fixedShippingAmount,
    JsonNode fixedShippingArea,
    JsonNode version) {
  public FulfillmentSettings settings() {
    return FulfillmentSettings.parse(
        bool(pickupEnabled),
        optionalString(pickupInstructions),
        bool(fixedShippingEnabled),
        optionalString(fixedShippingAmount),
        optionalString(fixedShippingArea));
  }

  public long expectedVersion() {
    if (version == null
        || !version.isIntegralNumber()
        || !version.canConvertToLong()
        || version.asLong() < 0) throw new IllegalArgumentException("Expected version");
    return version.asLong();
  }

  private static boolean bool(JsonNode node) {
    if (node == null || !node.isBoolean()) throw new IllegalArgumentException("Expected boolean");
    return node.asBoolean();
  }

  private static String optionalString(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (!node.isString()) throw new IllegalArgumentException("Expected string");
    return node.asString();
  }
}
