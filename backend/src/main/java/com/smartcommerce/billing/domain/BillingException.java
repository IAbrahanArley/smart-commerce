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
    /** Checkout asked while a subscription is running: plan changes go through the panel. */
    SUBSCRIPTION_EXISTS,
    /** Portal or a plan change asked without a running subscription. */
    NO_SUBSCRIPTION,
    /** A plan change, cancellation or resumption the subscription's state does not allow. */
    CHANGE_NOT_ALLOWED,
    /** An immediate change confirmed with a preview that is missing or too old. */
    PREVIEW_EXPIRED,
    /** The card declined the prorated charge of an upgrade: the plan stays as it was. */
    PAYMENT_DECLINED,
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
