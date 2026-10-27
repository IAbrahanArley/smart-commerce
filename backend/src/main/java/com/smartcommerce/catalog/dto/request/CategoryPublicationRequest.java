package com.smartcommerce.catalog.dto.request;

import tools.jackson.databind.JsonNode;

/** Shows ({@code true}) or hides the category in the storefront; a strict boolean (SC-041). */
public record CategoryPublicationRequest(JsonNode published) {
  public boolean value() {
    if (published == null || !published.isBoolean())
      throw new IllegalArgumentException("Expected boolean publication");
    return published.asBoolean();
  }
}
