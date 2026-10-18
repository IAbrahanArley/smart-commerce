package com.smartcommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.domain.StockLevel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationPolicyTest {
  private static final Instant CREATED = Instant.parse("2026-10-17T12:00:00.123456789Z");
  private static final Clock FIXED = Clock.fixed(CREATED, ZoneOffset.UTC);
  private final ReservationPolicy policy = new ReservationPolicy(ReservationPolicy.DEFAULT_TTL);

  @Test
  void deadlineIsThirtyMinutesAfterTheBackendClockAtDatabasePrecision() {
    Instant now = ReservationPolicy.now(FIXED);
    assertThat(now).isEqualTo(Instant.parse("2026-10-17T12:00:00.123456Z"));
    assertThat(policy.expiresAt(now)).isEqualTo(Instant.parse("2026-10-17T12:30:00.123456Z"));
  }

  @Test
  void reservationCountsBeforeButNotExactlyAtOrAfterTheDeadline() {
    Instant expiresAt = policy.expiresAt(ReservationPolicy.now(FIXED));
    Clock before = Clock.offset(FIXED, Duration.ofMinutes(30).minusNanos(1_000));
    Clock exactly = Clock.offset(FIXED, Duration.ofMinutes(30));
    Clock after = Clock.offset(FIXED, Duration.ofMinutes(30).plusNanos(1_000));
    assertThat(ReservationPolicy.active(expiresAt, ReservationPolicy.now(before))).isTrue();
    assertThat(ReservationPolicy.active(expiresAt, ReservationPolicy.now(exactly))).isFalse();
    assertThat(ReservationPolicy.active(expiresAt, ReservationPolicy.now(after))).isFalse();
  }

  @Test
  void rejectsWindowsOutsideThePlatformRangeAndInvalidLinesOrLevels() {
    for (Duration invalid :
        new Duration[] {null, Duration.ZERO, Duration.ofSeconds(59), Duration.ofHours(73)})
      assertThatThrownBy(() -> new ReservationPolicy(invalid))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReservationLine(UUID.randomUUID(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReservationLine(null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StockLevel(UUID.randomUUID(), 1, 2, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
