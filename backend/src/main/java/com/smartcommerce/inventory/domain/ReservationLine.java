package com.smartcommerce.inventory.domain;

import java.util.UUID;

public record ReservationLine(UUID variantId, int quantity) {
  public ReservationLine {
    if (variantId == null || quantity <= 0)
      throw new IllegalArgumentException("Invalid reservation line");
  }
}
