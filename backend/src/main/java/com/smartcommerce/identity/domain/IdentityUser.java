package com.smartcommerce.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** Global identity only; it confers no company, role or tenant authorization. */
public record IdentityUser(
    UUID id,
    EmailAddress email,
    IdentityStatus status,
    long authVersion,
    Instant createdAt,
    Instant updatedAt,
    Instant verifiedAt) {
  @Override
  public String toString() {
    return "IdentityUser[REDACTED]";
  }
}
