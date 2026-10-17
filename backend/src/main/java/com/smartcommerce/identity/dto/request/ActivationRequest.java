package com.smartcommerce.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ActivationRequest(
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String token,
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String password) {
  @Override
  public String toString() {
    return "Activation[REDACTED]";
  }
}
