package com.smartcommerce.orders.domain;

/** Counter sale failures (SC-053). Messages are codes only. */
public final class CounterSaleException extends RuntimeException {
  public enum Reason {
    /** Store, sale, product, cash session or account absent or foreign: one 404. */
    MISSING,
    /** The request id was already used for a different sale. */
    REQUEST_CONFLICT,
    /** Some item has fewer units available than sold, counting the storefront's reservations. */
    INSUFFICIENT_STOCK,
    /** An employee's discount above the OWNER's limit. */
    DISCOUNT_LIMIT,
    /** A Pix sale before the OWNER chose the Pix account. */
    PIX_NOT_CONFIGURED,
    /** A cash sale into a cash session that is not open. */
    SESSION_CLOSED,
    /** The Pix account must be a bank account (not a drawer, not Mercado Pago). */
    PROVIDER_READ_ONLY
  }

  private final Reason reason;

  public CounterSaleException(Reason reason) {
    super(reason.name(), null, false, false);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
