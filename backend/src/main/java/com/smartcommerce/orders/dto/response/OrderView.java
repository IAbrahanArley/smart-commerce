package com.smartcommerce.orders.dto.response;

import com.smartcommerce.catalog.domain.VariantOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Buyer-facing order: status, item snapshots, totals and delivery. No SKU, cost, buyer contact
 * data, tenant or store identifiers. A pending order past its reservation deadline reads EXPIRED.
 */
public record OrderView(
    UUID id,
    long number,
    String status,
    String currency,
    List<Item> items,
    String subtotal,
    String shipping,
    String total,
    Fulfillment fulfillment,
    Instant reservationExpiresAt,
    Instant createdAt) {
  public record Item(
      String productName,
      List<VariantOption> options,
      String unitPrice,
      int quantity,
      String lineTotal) {}

  public record Fulfillment(String type, String text) {}
}
