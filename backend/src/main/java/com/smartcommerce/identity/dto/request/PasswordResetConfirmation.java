package com.smartcommerce.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** The recovery link's token and the new password (MVP block 1a). */
public record PasswordResetConfirmation(
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String token,
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String password) {
  @Override
  public String toString() {
    return "PasswordResetConfirmation[REDACTED]";
  }
}
