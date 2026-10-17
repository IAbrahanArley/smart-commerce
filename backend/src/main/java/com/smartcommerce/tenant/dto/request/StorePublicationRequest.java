package com.smartcommerce.tenant.dto.request;

import tools.jackson.databind.JsonNode;

public record StorePublicationRequest(JsonNode published) {
  public boolean value() {
    if (published == null || !published.isBoolean())
      throw new IllegalArgumentException("Expected boolean publication");
    return published.asBoolean();
  }
}
