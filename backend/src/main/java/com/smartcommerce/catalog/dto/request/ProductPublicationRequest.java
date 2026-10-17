package com.smartcommerce.catalog.dto.request;

import tools.jackson.databind.JsonNode;

public record ProductPublicationRequest(JsonNode published, JsonNode version) {
  public boolean value() {
    if (published == null || !published.isBoolean())
      throw new IllegalArgumentException("Expected boolean publication");
    return published.asBoolean();
  }

  public long expectedVersion() {
    if (version == null
        || !version.isIntegralNumber()
        || !version.canConvertToLong()
        || version.asLong() < 0
        || version.asLong() == Long.MAX_VALUE)
      throw new IllegalArgumentException("Expected version");
    return version.asLong();
  }
}
