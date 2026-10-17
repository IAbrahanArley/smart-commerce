package com.smartcommerce.storefront.dto.request;

import com.smartcommerce.storefront.domain.CartLine;
import com.smartcommerce.storefront.domain.PublicLocator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict body: exactly {"items":[{"variantId":"uuid","quantity":1..10}]}, 1–20 unique lines. */
public record CartQuoteRequest(List<CartLine> lines) {
  public static final int MAX_BYTES = 16 * 1024;
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  public static CartQuoteRequest parse(byte[] body) {
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (JacksonException invalid) {
      throw new IllegalArgumentException("Invalid cart JSON");
    }
    if (root == null || !root.isObject() || root.size() != 1 || !root.has("items"))
      throw new IllegalArgumentException("Invalid cart");
    JsonNode items = root.get("items");
    if (!items.isArray() || items.isEmpty() || items.size() > CartLine.MAX_LINES)
      throw new IllegalArgumentException("Invalid cart lines");
    List<CartLine> lines = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    for (JsonNode item : items) {
      if (!item.isObject() || item.size() != 2) throw new IllegalArgumentException("Invalid line");
      JsonNode variant = item.get("variantId");
      JsonNode quantity = item.get("quantity");
      if (variant == null
          || !variant.isString()
          || quantity == null
          || !quantity.isIntegralNumber()
          || !quantity.canConvertToInt()) throw new IllegalArgumentException("Invalid line");
      var line = new CartLine(PublicLocator.uuid(variant.asString()), quantity.asInt());
      if (!seen.add(line.variantId())) throw new IllegalArgumentException("Duplicate line");
      lines.add(line);
    }
    return new CartQuoteRequest(List.copyOf(lines));
  }
}
