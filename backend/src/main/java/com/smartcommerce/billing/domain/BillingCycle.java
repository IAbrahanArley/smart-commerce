package com.smartcommerce.billing.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Billing cycles approved on 2026-10-26 (semiannual −10%, annual −20%). Each plan and cycle maps to
 * one recurring Stripe price found by its lookup key, such as {@code profissional_anual} (ADR-0017
 * B4); the amounts live in Stripe and on the landing ({@code admin-web/src/lib/plans.ts}).
 */
public enum BillingCycle {
  MONTHLY("mensal", 1),
  SEMIANNUAL("semestral", 6),
  ANNUAL("anual", 12);

  private final String suffix;
  private final int months;

  BillingCycle(String suffix, int months) {
    this.suffix = suffix;
    this.months = months;
  }

  public int months() {
    return months;
  }

  public static String lookupKey(Plan plan, BillingCycle cycle) {
    return plan.name().toLowerCase(Locale.ROOT) + "_" + cycle.suffix;
  }

  /** The plan and cycle of a price's lookup key; empty for any key this platform did not create. */
  public static Optional<Choice> parse(String lookupKey) {
    if (lookupKey == null) return Optional.empty();
    for (Plan plan : Plan.values())
      for (BillingCycle cycle : values())
        if (lookupKey(plan, cycle).equals(lookupKey)) return Optional.of(new Choice(plan, cycle));
    return Optional.empty();
  }

  public record Choice(Plan plan, BillingCycle cycle) {}
}
