package com.smartcommerce.tenant.exceptions;

public final class TenantPersistenceException extends RuntimeException {
  public enum Reason {
    MISSING,
    INACTIVE_IDENTITY,
    SLUG_CONFLICT
  }

  private final Reason reason;

  public TenantPersistenceException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
