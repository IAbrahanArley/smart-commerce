package com.smartcommerce.catalog.domain;

public final class ImageException extends RuntimeException {
  public enum Reason {
    MISSING,
    VERSION_CONFLICT,
    IMAGE_LIMIT,
    INVALID,
    TOO_LARGE,
    BUSY
  }

  private final Reason reason;

  public ImageException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
