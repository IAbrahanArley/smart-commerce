package com.smartcommerce.identity.exceptions;

/** Application failure with no transport status or submitted secrets. */
public final class IdentityException extends RuntimeException {
  public enum Reason {
    INVALID_PROOF,
    INVALID_CREDENTIALS,
    THROTTLED,
    UNAVAILABLE
  }

  private final Reason reason;

  public IdentityException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
