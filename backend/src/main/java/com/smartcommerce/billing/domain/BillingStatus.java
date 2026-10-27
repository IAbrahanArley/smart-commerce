package com.smartcommerce.billing.domain;

/**
 * Effective state of a billing account (ADR-0017 B7). TRIAL_EXPIRED is never stored: it is a TRIAL
 * past its end. PAST_DUE turns into SUSPENDED after the grace period, also without a scheduler.
 */
public enum BillingStatus {
  TRIAL,
  TRIAL_EXPIRED,
  ACTIVE,
  PAST_DUE,
  SUSPENDED,
  CANCELED;

  /** The panel accepts changes and the storefront stays online. */
  public boolean inGoodStanding() {
    return this == TRIAL || this == ACTIVE || this == PAST_DUE;
  }
}
