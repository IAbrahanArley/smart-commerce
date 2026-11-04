package com.smartcommerce.finance.domain;

/** Store credit failures (SC-055). Messages are codes only, never personal data. */
public final class CreditException extends RuntimeException {
  public enum Reason {
    /** Store, customer, account or cash session absent or foreign, or not allowed: one 404. */
    MISSING,
    /** The request id was already used for a different receipt. */
    REQUEST_CONFLICT,
    /** A receipt into a cash session that is not open. */
    SESSION_CLOSED,
    /** Receipts go into a drawer's open session or a bank account, never Mercado Pago. */
    ACCOUNT_NOT_ALLOWED,
    /** A receipt beyond what the customer owes. */
    OVERPAYMENT
  }

  private final Reason reason;

  public CreditException(Reason reason) {
    super(reason.name(), null, false, false);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
