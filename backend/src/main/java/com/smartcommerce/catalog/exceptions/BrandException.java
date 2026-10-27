package com.smartcommerce.catalog.exceptions;

/** Brand outcomes (SC-041); the controller advice owns HTTP translation. */
public final class BrandException extends RuntimeException {
  public enum Reason {
    MISSING,
    NAME_CONFLICT,
    LIMIT
  }

  private final Reason reason;

  public BrandException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
