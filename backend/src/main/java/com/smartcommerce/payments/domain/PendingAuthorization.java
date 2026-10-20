package com.smartcommerce.payments.domain;

import java.time.Instant;
import java.util.UUID;

/** A stored OAuth attempt, read under lock when the provider redirects back. */
public record PendingAuthorization(
    UUID tenant, UUID user, byte[] codeVerifier, Instant expiresAt, Instant usedAt) {
  @Override
  public String toString() {
    return "PendingAuthorization[" + tenant + "]";
  }
}
