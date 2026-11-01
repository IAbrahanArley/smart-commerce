package com.smartcommerce.finance.domain;

/** Finance failures. Messages are codes only. */
public final class FinanceException extends RuntimeException {
  public enum Reason {
    /** Store or expense absent, or not owned by the actor: one 404 for both. */
    MISSING,
    /** The request id was already used for a different expense, account or movement. */
    REQUEST_CONFLICT,
    /** The store already has the maximum number of accounts (SC-050). */
    ACCOUNT_LIMIT,
    /** A transfer from an account to itself (SC-050). */
    SAME_ACCOUNT,
    /** A manual adjustment or transfer on the Mercado Pago account, which is read-only. */
    PROVIDER_READ_ONLY
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
