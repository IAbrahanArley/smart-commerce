package com.smartcommerce.catalog.dto.request;

import com.smartcommerce.catalog.domain.ProductValues;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The "Vitrine" card of a product (SC-041): its brand ({@code null} clears it), its address and the
 * "compare at" price of each listed variant (a decimal string, or {@code null} to clear it).
 * Variants left out keep their value; {@code featured} left out keeps it too (SC-043).
 */
public record ProductStorefrontRequest(
    UUID brandId, String slug, List<VariantCompareAt> variants, Boolean featured) {
  public record VariantCompareAt(UUID id, JsonNode compareAtPrice) {
    /** {@code null} clears the price. */
    public BigDecimal value() {
      if (id == null) throw new IllegalArgumentException("Expected variant");
      String text = ProductRequest.decimal(compareAtPrice, true);
      return text == null ? null : ProductValues.amount(text, true);
    }
  }

  public List<VariantCompareAt> variantList() {
    if (variants == null) return List.of();
    if (variants.size() > 50 || variants.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Invalid variant list");
    return variants;
  }
}
