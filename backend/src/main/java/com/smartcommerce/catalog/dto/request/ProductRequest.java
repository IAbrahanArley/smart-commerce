package com.smartcommerce.catalog.dto.request;

import com.smartcommerce.catalog.domain.*;
import com.smartcommerce.catalog.domain.ProductValues;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record ProductRequest(
    String name,
    String sku,
    JsonNode price,
    JsonNode cost,
    JsonNode version,
    String descriptionHtml,
    String kind,
    List<VariantRequest> variants) {
  public ProductValues values() {
    validateVariants();
    if ("VARIABLE".equals(kind)) {
      if (variants != null && !variants.isEmpty()) return variants.getFirst().values(name).values();
      return ProductValues.parse(name, "VARIABLE", "1", null);
    }
    return ProductValues.parse(name, sku, decimal(price, false), decimal(cost, true));
  }

  public ProductDetails details() {
    validateVariants();
    return new ProductDetails(
        descriptionHtml == null ? null : ProductDescription.sanitize(descriptionHtml),
        kind == null ? null : ProductKind.valueOf(kind),
        variants == null ? null : variants.stream().map(v -> v.values(name)).toList());
  }

  private void validateVariants() {
    if (variants != null
        && (variants.size() > 50 || variants.stream().anyMatch(java.util.Objects::isNull)))
      throw new IllegalArgumentException("Invalid variant list");
  }

  public static String decimal(JsonNode node, boolean optional) {
    if (optional && (node == null || node.isNull())) return null;
    if (node == null || !node.isString())
      throw new IllegalArgumentException("Expected decimal string");
    return node.asString();
  }

  public long expectedVersion() {
    return version(version);
  }

  public static long version(JsonNode version) {
    if (version == null
        || !version.isIntegralNumber()
        || !version.canConvertToLong()
        || version.asLong() < 0) throw new IllegalArgumentException("Expected version");
    return version.asLong();
  }
}
