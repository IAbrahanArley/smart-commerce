package com.smartcommerce.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistence projections for the store OWNER (SC-010); tenant always from the membership. */
public final class AdminOrderRecords {
  private AdminOrderRecords() {}

  /** Order row locked for a state change, already authorized for the actor. */
  public record Owned(UUID id, UUID tenantId, String status, Instant reservationExpiresAt) {}

  /** Due order picked by the expiration batch; keeps its own tenant. */
  public record Due(UUID id, UUID tenantId) {}

  /** List row; {@code status} is already the effective one (pending past deadline = EXPIRED). */
  public record Summary(
      UUID id,
      long number,
      String status,
      BigDecimal total,
      String buyerName,
      FulfillmentType fulfillment,
      int units,
      Instant createdAt,
      Instant reservationExpiresAt) {}

  /** Full header for the OWNER; buyer and address fields are null once anonymized. */
  public record Detail(
      UUID id,
      long number,
      String status,
      BigDecimal subtotal,
      BigDecimal shipping,
      BigDecimal total,
      FulfillmentType fulfillment,
      String fulfillmentText,
      String buyerName,
      String buyerEmail,
      String buyerPhone,
      String line1,
      String line2,
      String district,
      String city,
      String state,
      String postalCode,
      Instant reservationExpiresAt,
      Instant createdAt,
      Instant expiredAt,
      Instant cancelledAt,
      String cancelReason,
      Instant anonymizedAt,
      Instant paidAt,
      boolean paidWithoutStock,
      Instant refundedAt) {}

  /** Item snapshot with the private fields the OWNER may see (SKU and unit cost). */
  public record Item(
      String productName,
      String optionsJson,
      String sku,
      BigDecimal unitPrice,
      BigDecimal unitCost,
      int quantity,
      BigDecimal total) {}
}
