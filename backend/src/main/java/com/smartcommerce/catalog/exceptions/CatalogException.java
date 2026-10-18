package com.smartcommerce.catalog.exceptions;

/** Application outcome; the controller advice owns HTTP translation. */
public final class CatalogException extends RuntimeException {
  public enum Reason {
    MISSING,
    SKU_CONFLICT,
    VERSION_CONFLICT,
    PRODUCT_KIND_CONFLICT,
    VARIANT_LIMIT,
    COMBINATION_CONFLICT,
    VARIANT_REQUIRED
  }

  private final Reason reason;

  public CatalogException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
