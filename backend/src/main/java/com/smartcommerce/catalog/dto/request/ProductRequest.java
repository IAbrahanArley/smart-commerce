package com.smartcommerce.catalog.dto.request;

import com.smartcommerce.catalog.domain.ProductValues;
import tools.jackson.databind.JsonNode;

public record ProductRequest(
    String name, String sku, JsonNode price, JsonNode cost, JsonNode version) {
  public ProductValues values() {
    return ProductValues.parse(name, sku, decimal(price, false), decimal(cost, true));
  }

  private static String decimal(JsonNode node, boolean optional) {
    if (optional && (node == null || node.isNull())) return null;
    if (node == null || !node.isString())
      throw new IllegalArgumentException("Expected decimal string");
    return node.asString();
  }

  public long expectedVersion() {
    if (version == null
        || !version.isIntegralNumber()
        || !version.canConvertToLong()
        || version.asLong() < 0) throw new IllegalArgumentException("Expected version");
    return version.asLong();
  }
}
