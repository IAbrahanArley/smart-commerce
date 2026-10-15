package com.smartcommerce.identity;

import java.time.Instant;
import java.util.UUID;

public record LocalCredential(
    UUID userId, PasswordHash hash, Instant createdAt, Instant updatedAt) {
  @Override
  public String toString() {
    return "LocalCredential[REDACTED]";
  }
}
