package com.smartcommerce.orders.domain;

/**
 * Admin list input (SC-010): optional effective status and the number cursor, validated before any
 * query. {@code after} is the last number of the previous page (numbers are listed newest first).
 */
public record AdminOrderQuery(String status, Long after) {
  public static final int PAGE_SIZE = 20;

  public static AdminOrderQuery of(String status, String after) {
    String filter = status == null || status.isBlank() ? null : status;
    if (filter != null
        && !filter.equals("PENDING_PAYMENT")
        && !filter.equals("EXPIRED")
        && !filter.equals("CANCELLED")) throw new IllegalArgumentException("Invalid status");
    Long cursor = null;
    if (after != null && !after.isBlank()) {
      if (!after.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Invalid cursor");
      cursor = Long.parseLong(after);
    }
    return new AdminOrderQuery(filter, cursor);
  }
}
