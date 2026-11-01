package com.smartcommerce.billing.dto.response;

import java.time.Instant;

/**
 * The owner's Stripe subscription as Stripe reports it now (ADR-0017 B13). Every field is null and
 * {@code cancelAtPeriodEnd} false when there is no running subscription; {@code pendingChange} is
 * the smaller plan or shorter cycle scheduled for the end of the period.
 */
public record SubscriptionView(
    String status,
    String plan,
    String cycle,
    Instant currentPeriodEnd,
    boolean cancelAtPeriodEnd,
    Pending pendingChange) {
  public record Pending(String plan, String cycle, Instant startsAt) {}

  public static SubscriptionView none() {
    return new SubscriptionView(null, null, null, null, false, null);
  }
}
