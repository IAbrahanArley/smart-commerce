package com.smartcommerce.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Validated values; absence of acquisition cost is never converted into zero. */
public record ProductValues(String name, String sku, BigDecimal price, BigDecimal cost) {
  public static ProductValues parse(String name, String sku, String price, String cost) {
    if (name == null
        || name.length() > 512
        || name.codePoints().anyMatch(ProductValues::invalidText)) {
      throw new IllegalArgumentException("Invalid product name");
    }
    name = name.strip();
    if (name.isBlank() || name.codePointCount(0, name.length()) > 120) {
      throw new IllegalArgumentException("Invalid product name");
    }
    if (sku == null || sku.length() > 256) throw new IllegalArgumentException("Invalid SKU");
    sku = sku.strip();
    if (!sku.matches("[A-Za-z0-9._/-]{1,64}")) throw new IllegalArgumentException("Invalid SKU");
    return new ProductValues(
        name, sku, amount(price, true), cost == null ? null : amount(cost, false));
  }

  /** A BRL amount as a decimal string with up to 2 places; zero only when not {@code positive}. */
  public static BigDecimal amount(String input, boolean positive) {
    if (input == null || !input.matches("[0-9]{1,17}(?:\\.[0-9]{1,2})?")) {
      throw new IllegalArgumentException("Invalid BRL amount");
    }
    BigDecimal amount = new BigDecimal(input).setScale(2, RoundingMode.UNNECESSARY);
    if (positive && amount.signum() == 0)
      throw new IllegalArgumentException("Price must be positive");
    return amount;
  }

  private static boolean invalidText(int code) {
    int type = Character.getType(code);
    return Character.isISOControl(code)
        || type == Character.FORMAT
        || type == Character.SURROGATE
        || type == Character.LINE_SEPARATOR
        || type == Character.PARAGRAPH_SEPARATOR;
  }
}
