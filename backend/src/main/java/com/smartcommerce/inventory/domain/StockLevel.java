package com.smartcommerce.inventory.domain;

import java.util.UUID;

/** Physical, reserved (ACTIVE and not expired) and available units of one variant. */
public record StockLevel(UUID variantId, int onHand, long reserved, int available) {
  public StockLevel {
    if (variantId == null || onHand < 0 || reserved < 0 || available < 0)
      throw new IllegalArgumentException("Invalid stock level");
  }
}
