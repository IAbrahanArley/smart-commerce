package com.smartcommerce.finance.domain;

/** Finance failures. Messages are codes only. */
public final class FinanceException extends RuntimeException {
  public enum Reason {
    /** Store or expense absent, or not owned by the actor: one 404 for both. */
    MISSING,
    /** The request id was already used for a different expense. */
    REQUEST_CONFLICT
  }

  private final Reason reason;

  public FinanceException(Reason reason) {
    super(reason.name(), null, false, false);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
