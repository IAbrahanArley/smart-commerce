package com.smartcommerce.catalog.dto.request;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Where the category goes (SC-041): under {@code parentId} ({@code null}: a root), at {@code
 * position} among its siblings (0 is first; larger values place it last).
 */
public record CategoryPlacementRequest(UUID parentId, JsonNode position) {
  public int value() {
    if (position == null
        || !position.isIntegralNumber()
        || !position.canConvertToInt()
        || position.asInt() < 0) throw new IllegalArgumentException("Expected position");
    return position.asInt();
  }
}
