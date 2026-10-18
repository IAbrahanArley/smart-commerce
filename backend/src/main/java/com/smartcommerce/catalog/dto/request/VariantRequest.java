package com.smartcommerce.catalog.dto.request;

import com.smartcommerce.catalog.domain.*;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record VariantRequest(
    String sku, JsonNode price, JsonNode cost, JsonNode version, List<VariantOption> options) {
  public VariantValues values(String name) {
    return new VariantValues(
        ProductValues.parse(
            name, sku, ProductRequest.decimal(price, false), ProductRequest.decimal(cost, true)),
        options == null ? List.of() : options);
  }

  public long expectedVersion() {
    return ProductRequest.version(version);
  }
}
