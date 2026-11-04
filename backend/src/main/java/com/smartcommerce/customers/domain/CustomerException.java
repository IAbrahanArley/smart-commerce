package com.smartcommerce.customers.domain;

/** Store customer failures (SC-055). Messages are codes only, never personal data. */
public final class CustomerException extends RuntimeException {
  public enum Reason {
    /** Store or customer absent, foreign, or the actor may not do this: one 404. */
    MISSING,
    /** The request id was already used for a different customer. */
    REQUEST_CONFLICT,
    /** Another customer of the store already has this e-mail (ADR-0021 R1). */
    EMAIL_TAKEN
  }

  private final Reason reason;

  public CustomerException(Reason reason) {
    super(reason.name(), null, false, false);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
