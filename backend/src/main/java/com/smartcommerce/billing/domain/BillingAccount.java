package com.smartcommerce.billing.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Stored billing account of an owner; {@code plan} and {@code cycle} are null during the trial. */
public record BillingAccount(
    UUID userId,
    String status,
    Plan plan,
    String cycle,
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    Instant pastDueSince) {

  /** The state at {@code now}: the trial ends by time, and so does the grace of a failed charge. */
  public BillingStatus effective(Instant now, Duration grace) {
    return switch (status) {
      case "TRIAL" -> now.isBefore(trialEndsAt) ? BillingStatus.TRIAL : BillingStatus.TRIAL_EXPIRED;
      case "ACTIVE" -> BillingStatus.ACTIVE;
      case "PAST_DUE" ->
          now.isBefore(pastDueSince.plus(grace)) ? BillingStatus.PAST_DUE : BillingStatus.SUSPENDED;
      case "SUSPENDED" -> BillingStatus.SUSPENDED;
      case "CANCELED" -> BillingStatus.CANCELED;
      default -> throw new IllegalStateException("Unknown billing status");
    };
  }

  /** Limits in force: the trial unlocks the Profissional plan (ADR-0017 B2). */
  public Plan limits() {
    return plan == null ? Plan.PROFISSIONAL : plan;
  }
}
