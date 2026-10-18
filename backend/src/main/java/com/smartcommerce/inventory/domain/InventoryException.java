package com.smartcommerce.inventory.domain;

public final class InventoryException extends RuntimeException {
  public enum Reason {
    MISSING,
    INSUFFICIENT_STOCK,
    RESERVED_STOCK,
    BALANCE_LIMIT,
    REQUEST_CONFLICT
  }

  private final Reason reason;
  private final long reserved;

  public InventoryException(Reason reason) {
    this(reason, 0);
  }

  /** {@code reserved} is only meaningful for RESERVED_STOCK, shown to the store OWNER (D7). */
  public InventoryException(Reason reason, long reserved) {
    super(reason.name());
    this.reason = reason;
    this.reserved = reserved;
  }

  public Reason reason() {
    return reason;
  }

  public long reserved() {
    return reserved;
  }
}
