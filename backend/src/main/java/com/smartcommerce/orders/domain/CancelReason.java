package com.smartcommerce.orders.domain;

/** Store OWNER's reason to cancel (SC-010): one line, 3 to 300 characters, admin-only. */
public record CancelReason(String text) {
  public static final int MIN = 3;
  public static final int MAX = 300;

  public static CancelReason of(String value) {
    String text = Text.required(value, MAX);
    if (text.codePointCount(0, text.length()) < MIN)
      throw new IllegalArgumentException("Reason too short");
    return new CancelReason(text);
  }
}
