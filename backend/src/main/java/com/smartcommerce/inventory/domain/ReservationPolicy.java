package com.smartcommerce.inventory.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Reservation window (ADR-0012 D1). A reservation counts while {@code expiresAt > now}; at the
 * exact deadline it no longer counts, matching {@code inventory_reserved_quantity} in V11.
 */
public record ReservationPolicy(Duration ttl) {
  public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final Duration MAX_TTL = Duration.ofHours(72);

  public ReservationPolicy {
    if (ttl == null || ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(MAX_TTL) > 0)
      throw new IllegalArgumentException("Reservation TTL must be between 1 minute and 72 hours");
  }

  /** PostgreSQL stores microseconds: comparisons must use the persisted precision. */
  public static Instant now(Clock clock) {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  public Instant expiresAt(Instant createdAt) {
    return createdAt.plus(ttl);
  }

  public static boolean active(Instant expiresAt, Instant now) {
    return expiresAt.isAfter(now);
  }
}
