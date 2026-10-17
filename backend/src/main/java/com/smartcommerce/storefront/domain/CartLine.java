package com.smartcommerce.storefront.domain;

import java.util.UUID;

/** One untrusted cart line: a public variant selector and a bounded quantity (ADR-0012 §2). */
public record CartLine(UUID variantId, int quantity) {
  public static final int MAX_LINES = 20;
  public static final int MAX_QUANTITY = 10;

  public CartLine {
    if (variantId == null || quantity < 1 || quantity > MAX_QUANTITY)
      throw new IllegalArgumentException("Invalid cart line");
  }
}
