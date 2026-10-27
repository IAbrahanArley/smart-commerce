package com.smartcommerce.tenant.exceptions;

/** Application outcome; the controller advice owns HTTP translation. */
public final class StoreProfileException extends RuntimeException {
  public enum Reason {
    MISSING,
    VERSION_CONFLICT,
    NO_LOGO
  }

  private final Reason reason;

  public StoreProfileException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
