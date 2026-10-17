package com.smartcommerce.catalog.exceptions;

/** Persistence failures carry no HTTP status, database values or SQL causes. */
public final class CatalogPersistenceException extends RuntimeException {
  public enum Reason {
    MISSING,
    VERSION_CONFLICT
  }

  private final Reason reason;

  public CatalogPersistenceException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
