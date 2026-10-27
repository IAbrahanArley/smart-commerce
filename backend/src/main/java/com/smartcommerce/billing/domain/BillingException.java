package com.smartcommerce.billing.domain;

/**
 * A plan limit or an account not in good standing (ADR-0017 B7, B9). The message is safe to show to
 * the owner; the reason becomes the response code.
 */
public final class BillingException extends RuntimeException {
  public enum Reason {
    /** Trial expired, suspended or canceled: changes wait for the subscription. */
    PLAN_INACTIVE,
    /** The plan's number of companies, products or questions is used up. */
    PLAN_LIMIT
  }

  private final Reason reason;

  public BillingException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
