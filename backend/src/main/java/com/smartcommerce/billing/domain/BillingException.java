package com.smartcommerce.billing.domain;

/**
 * A plan limit, an account not in good standing or a refused Stripe operation (ADR-0017). The
 * message is safe to show to the owner; the reason becomes the response code.
 */
public final class BillingException extends RuntimeException {
  public enum Reason {
    /** Trial expired, suspended or canceled: changes wait for the subscription. */
    PLAN_INACTIVE,
    /** The plan's number of companies, products or questions is used up. */
    PLAN_LIMIT,
    /** Checkout asked while a subscription is running: plan changes go through the portal. */
    SUBSCRIPTION_EXISTS,
    /** Portal asked before any checkout: there is no Stripe customer yet. */
    NO_SUBSCRIPTION,
    /** Webhook without a valid signature or with a body that is not a Stripe event. */
    INVALID_EVENT,
    /** Billing is off ({@code billing.enabled=false}): the Stripe endpoints do not exist. */
    DISABLED
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
