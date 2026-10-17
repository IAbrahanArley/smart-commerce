package com.smartcommerce.inventory.domain;

public final class InventoryException extends RuntimeException {
  public enum Reason {
    MISSING,
    INSUFFICIENT_STOCK,
    BALANCE_LIMIT,
    REQUEST_CONFLICT
  }

  private final Reason reason;

  public InventoryException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
