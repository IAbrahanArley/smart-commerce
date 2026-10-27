package com.smartcommerce.catalog.exceptions;

/** Outcomes of the product "Vitrine" card (SC-041); the controller advice owns HTTP translation. */
public final class ProductStorefrontException extends RuntimeException {
  public enum Reason {
    MISSING,
    SLUG_CONFLICT,
    /** A "compare at" price not greater than the variant's current price. */
    COMPARE_AT_NOT_ABOVE
  }

  private final Reason reason;

  public ProductStorefrontException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
