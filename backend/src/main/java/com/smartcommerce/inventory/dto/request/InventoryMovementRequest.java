package com.smartcommerce.inventory.dto.request;

import com.smartcommerce.inventory.domain.InventoryInput;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record InventoryMovementRequest(UUID requestId, String type, JsonNode delta, String reason) {
  public InventoryInput values() {
    if (delta == null || !delta.isIntegralNumber() || !delta.canConvertToInt())
      throw new IllegalArgumentException("Expected integral quantity");
    return InventoryInput.parse(requestId, type, delta.asInt(), reason);
  }
}
