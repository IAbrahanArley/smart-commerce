package com.smartcommerce.orders.domain;

import java.math.BigDecimal;

public final class OrderException extends RuntimeException {
  public enum Reason {
    /** Store or order absent, unpublished, foreign, wrong token or checkout disabled: 404. */
    MISSING,
    ITEM_UNAVAILABLE,
    FULFILLMENT_UNAVAILABLE,
    PRICE_CHANGED,
    INSUFFICIENT_STOCK,
    REQUEST_CONFLICT,
    PENDING_LIMIT,
    RATE_LIMITED,
    /** Admin cancellation of an order whose reservation deadline already passed (SC-010). */
    ORDER_EXPIRED,
    /** Payment requested for an order that no longer awaits it: expired or cancelled (SC-013). */
    NOT_PAYABLE,
    /** The store cannot take payments now: integration off or account not connected (SC-013). */
    PAYMENT_UNAVAILABLE,
    /** The provider failed or answered unexpectedly; the order stays pending (SC-013). */
    PAYMENT_FAILED
  }

  private final Reason reason;
  private final BigDecimal currentTotal;

  public OrderException(Reason reason) {
    this(reason, null);
  }

  /** {@code currentTotal} is only set for PRICE_CHANGED: the total recomputed by the backend. */
  public OrderException(Reason reason, BigDecimal currentTotal) {
    super(reason.name(), null, false, false);
    this.reason = reason;
    this.currentTotal = currentTotal;
  }

  public Reason reason() {
    return reason;
  }

  public BigDecimal currentTotal() {
    return currentTotal;
  }
}
