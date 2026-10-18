package com.smartcommerce.tenant.exceptions;

/** Application outcome; the controller advice owns HTTP translation. */
public final class FulfillmentException extends RuntimeException {
  public enum Reason {
    MISSING,
    VERSION_CONFLICT
  }

  private final Reason reason;

  public FulfillmentException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
