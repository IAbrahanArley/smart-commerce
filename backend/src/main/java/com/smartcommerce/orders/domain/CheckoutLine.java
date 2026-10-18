package com.smartcommerce.orders.domain;

import java.util.UUID;

/** One untrusted checkout line: a public variant selector and a bounded quantity (D4). */
public record CheckoutLine(UUID variantId, int quantity) {
  public static final int MAX_LINES = 20;
  public static final int MAX_QUANTITY = 10;

  public CheckoutLine {
    if (variantId == null || quantity < 1 || quantity > MAX_QUANTITY)
      throw new IllegalArgumentException("Invalid checkout line");
  }
}
