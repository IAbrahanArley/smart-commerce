package com.smartcommerce.orders.dto.request;

import com.smartcommerce.orders.domain.CheckoutCommand;
import com.smartcommerce.orders.domain.CheckoutLine;
import com.smartcommerce.orders.domain.FulfillmentType;
import com.smartcommerce.orders.domain.OrderBuyer;
import com.smartcommerce.orders.domain.ShippingAddress;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict checkout body (unknown, missing or duplicated fields are rejected):
 *
 * <pre>{"requestId":"uuid","items":[{"variantId":"uuid","quantity":1}],
 * "fulfillment":{"type":"PICKUP"} | {"type":"FIXED_SHIPPING","address":{"line1":"…","line2":"…",
 * "district":"…","city":"…","state":"SP","postalCode":"01001000"}},
 * "buyer":{"name":"…","email":"…","phone":"…"},"expectedTotal":"59.90"}</pre>
 *
 * Money is a plain decimal string; prices never come from the client.
 */
public final class CheckoutRequest {
  public static final int MAX_BYTES = 8 * 1024;
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private CheckoutRequest() {}

  public static CheckoutCommand parse(byte[] body) {
    JsonNode root;
    try {
      root = JSON.readTree(body);
    } catch (JacksonException invalid) {
      throw new IllegalArgumentException("Invalid checkout JSON");
    }
    fields(root, Set.of("requestId", "items", "fulfillment", "buyer", "expectedTotal"), Set.of());
    JsonNode fulfillment = root.get("fulfillment");
    FulfillmentType type = fulfillmentType(fulfillment);
    ShippingAddress address = null;
    if (type == FulfillmentType.FIXED_SHIPPING) {
      fields(fulfillment, Set.of("type", "address"), Set.of());
      JsonNode node = fulfillment.get("address");
      fields(node, Set.of("line1", "district", "city", "state", "postalCode"), Set.of("line2"));
      address =
          new ShippingAddress(
              text(node, "line1"),
              node.has("line2") ? text(node, "line2") : null,
              text(node, "district"),
              text(node, "city"),
              text(node, "state"),
              text(node, "postalCode"));
    } else fields(fulfillment, Set.of("type"), Set.of());
    JsonNode buyer = root.get("buyer");
    fields(buyer, Set.of("name", "email", "phone"), Set.of());
    String expected = text(root, "expectedTotal");
    if (!expected.matches("[0-9]{1,17}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid expected total");
    return new CheckoutCommand(
        uuid(text(root, "requestId")),
        lines(root.get("items")),
        type,
        address,
        OrderBuyer.of(text(buyer, "name"), text(buyer, "email"), text(buyer, "phone")),
        new BigDecimal(expected));
  }

  private static FulfillmentType fulfillmentType(JsonNode fulfillment) {
    if (fulfillment == null || !fulfillment.isObject())
      throw new IllegalArgumentException("Invalid fulfillment");
    String type = text(fulfillment, "type");
    return switch (type) {
      case "PICKUP" -> FulfillmentType.PICKUP;
      case "FIXED_SHIPPING" -> FulfillmentType.FIXED_SHIPPING;
      default -> throw new IllegalArgumentException("Invalid fulfillment type");
    };
  }

  private static List<CheckoutLine> lines(JsonNode items) {
    if (items == null
        || !items.isArray()
        || items.isEmpty()
        || items.size() > CheckoutLine.MAX_LINES)
      throw new IllegalArgumentException("Invalid checkout lines");
    List<CheckoutLine> lines = new ArrayList<>();
    for (JsonNode item : items) {
      fields(item, Set.of("variantId", "quantity"), Set.of());
      JsonNode quantity = item.get("quantity");
      if (!quantity.isIntegralNumber() || !quantity.canConvertToInt())
        throw new IllegalArgumentException("Invalid quantity");
      lines.add(new CheckoutLine(uuid(text(item, "variantId")), quantity.asInt()));
    }
    return lines;
  }

  private static void fields(JsonNode node, Set<String> required, Set<String> optional) {
    if (node == null || !node.isObject()) throw new IllegalArgumentException("Invalid object");
    for (String name : required)
      if (!node.has(name)) throw new IllegalArgumentException("Missing field");
    for (String name : node.propertyNames())
      if (!required.contains(name) && !optional.contains(name))
        throw new IllegalArgumentException("Unknown field");
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString()) throw new IllegalArgumentException("Invalid field");
    return value.asString();
  }

  private static UUID uuid(String value) {
    if (!value.matches(UUID_PATTERN)) throw new IllegalArgumentException("Invalid identifier");
    return UUID.fromString(value);
  }
}
