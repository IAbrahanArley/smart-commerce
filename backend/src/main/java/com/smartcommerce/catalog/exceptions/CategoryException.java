package com.smartcommerce.catalog.exceptions;

/** Category outcomes (MVP block 1c); the controller advice owns HTTP translation. */
public final class CategoryException extends RuntimeException {
  public enum Reason {
    MISSING,
    NAME_CONFLICT,
    LIMIT
  }

  private final Reason reason;

  public CategoryException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
