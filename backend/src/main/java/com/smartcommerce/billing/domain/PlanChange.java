package com.smartcommerce.billing.domain;

/**
 * When a plan or cycle change takes effect (ADR-0017 B13, decided on 2026-11-01). Moving to a plan
 * at least as large on a cycle at least as long is an upgrade: it applies at once and the prorated
 * difference is charged right away. Any other move (a smaller plan, a shorter cycle, or both mixed)
 * waits for the end of the paid period, with no credit and no refund.
 */
public enum PlanChange {
  NOW,
  AT_PERIOD_END;

  public static PlanChange of(
      Plan currentPlan, BillingCycle currentCycle, Plan targetPlan, BillingCycle targetCycle) {
    if (currentPlan == targetPlan && currentCycle == targetCycle)
      throw new IllegalArgumentException("Same plan and cycle");
    boolean larger = targetPlan.ordinal() >= currentPlan.ordinal();
    boolean longer = targetCycle.months() >= currentCycle.months();
    return larger && longer ? NOW : AT_PERIOD_END;
  }
}
