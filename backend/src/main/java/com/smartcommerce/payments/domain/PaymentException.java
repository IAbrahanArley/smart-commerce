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
    NOT_CONNECTED
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
