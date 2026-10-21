package com.smartcommerce.payments.domain;

/** Payment connection failures. Messages are codes only: never tokens, codes or states. */
public final class PaymentException extends RuntimeException {
  public enum Reason {
    /** Store absent or not owned by the actor: one 404 for both. */
    MISSING,
    /** Platform credentials are not configured in this environment. */
    UNAVAILABLE,
    /** State absent, malformed, expired, reused or started by another user or store. */
    INVALID_STATE,
    /** The seller denied the authorization at the provider. */
    DENIED,
    /** The provider rejected the exchange or the refresh, or answered unexpectedly. */
    PROVIDER_ERROR,
    /** The provider says the grant is no longer valid (revoked or expired). */
    REVOKED,
    /** No connected account for this store. */
    NOT_CONNECTED,
    /** The store is now connected to another seller account than the one the order used. */
    ACCOUNT_CHANGED,
    /** Webhook signature missing, malformed, stale or wrong (SC-014). */
    INVALID_SIGNATURE,
    /** Authenticated notification that cannot be read (SC-014). */
    INVALID_NOTIFICATION,
    /** Refund asked for a payment that is not approved (SC-016). */
    NOT_REFUNDABLE,
    /** A refund already exists for this payment with another stock choice (SC-016). */
    REFUND_CONFLICT
  }

  private final Reason reason;

  public PaymentException(Reason reason) {
    super(reason.name(), null, false, false);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
