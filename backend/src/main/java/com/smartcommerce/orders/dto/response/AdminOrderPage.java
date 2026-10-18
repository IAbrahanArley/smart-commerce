package com.smartcommerce.orders.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin order list page; {@code nextCursor} is the number to pass as {@code after}, or null. */
public record AdminOrderPage(List<Summary> orders, String nextCursor) {
  public record Summary(
      UUID id,
      long number,
      String status,
      String total,
      String currency,
      String buyerName,
      String fulfillmentType,
      int units,
      Instant createdAt,
      Instant reservationExpiresAt) {}
}
