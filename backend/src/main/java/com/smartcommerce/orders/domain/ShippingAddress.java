package com.smartcommerce.orders.domain;

import java.util.Locale;

/**
 * Delivery address, collected only for fixed shipping (D2). Brazilian postal code and state are
 * checked for format only; the store area is informative and coverage is never validated.
 */
public record ShippingAddress(
    String line1, String line2, String district, String city, String state, String postalCode) {
  public ShippingAddress {
    line1 = Text.required(line1, 200);
    line2 = Text.optional(line2, 200);
    district = Text.required(district, 100);
    city = Text.required(city, 100);
    state = state == null ? null : state.strip().toUpperCase(Locale.ROOT);
    if (state == null || !state.matches("[A-Z]{2}"))
      throw new IllegalArgumentException("Invalid state");
    postalCode = postalCode == null ? null : postalCode.replaceAll("[\\s.-]", "");
    if (postalCode == null || !postalCode.matches("[0-9]{8}"))
      throw new IllegalArgumentException("Invalid postal code");
  }

  String canonical() {
    return String.join("\n", line1, line2 == null ? "" : line2, district, city, state, postalCode);
  }
}
