package com.smartcommerce.orders.dto.response;

import com.smartcommerce.catalog.domain.VariantOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order as seen by its store OWNER (SC-010): snapshots with SKU and unit cost, delivery, values,
 * buyer contact and address. {@code buyer} and {@code address} are null once anonymized (D2).
 */
public record AdminOrderView(
    UUID id,
    long number,
    String status,
    String currency,
    List<Item> items,
    String subtotal,
    String shipping,
    String total,
    OrderView.Fulfillment fulfillment,
    Buyer buyer,
    Address address,
    Instant reservationExpiresAt,
    Instant createdAt,
    Instant expiredAt,
    Cancellation cancellation,
    Instant anonymizedAt) {
  public record Item(
      String productName,
      List<VariantOption> options,
      String sku,
      String unitPrice,
      String unitCost,
      int quantity,
      String lineTotal) {}

  public record Buyer(String name, String email, String phone) {}

  public record Address(
      String line1, String line2, String district, String city, String state, String postalCode) {}

  public record Cancellation(String reason, Instant cancelledAt) {}
}
