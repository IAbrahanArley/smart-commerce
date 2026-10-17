package com.smartcommerce.tenant.exceptions;

/** Application outcome; the controller advice owns HTTP translation. */
public final class TenantException extends RuntimeException {
  public enum Reason {
    MISSING,
    SLUG_CONFLICT,
    INVALID_IDENTITY
  }

  private final Reason reason;

  public TenantException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
