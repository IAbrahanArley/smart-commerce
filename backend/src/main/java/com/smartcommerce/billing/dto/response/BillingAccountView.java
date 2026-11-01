package com.smartcommerce.billing.dto.response;

import java.time.Instant;

/**
 * The owner's plan, standing and limits (SC-031). {@code plan} and {@code cycle} are null in the
 * trial; {@code enforced} is false while billing is still switched off.
 */
public record BillingAccountView(
    String status,
    String plan,
    String cycle,
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    boolean enforced,
    Limits limits,
    long companies) {
  public record Limits(int companies, int productsPerStore, int monthlyQuestions, int staff) {}
}
