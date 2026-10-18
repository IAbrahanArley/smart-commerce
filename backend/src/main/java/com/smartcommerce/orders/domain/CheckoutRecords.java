package com.smartcommerce.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistence projections of the orders module. */
public final class CheckoutRecords {
  private CheckoutRecords() {}

  /** Published store resolved by the server from the slug; the tenant is never client input. */
  public record Store(UUID tenantId, UUID storeId) {}

  /** Published variant of the store, with private snapshot fields (SKU, cost) for the item. */
  public record Variant(
      UUID id,
      UUID productId,
      String productName,
      String sku,
      BigDecimal price,
      BigDecimal cost,
      String currency,
      String optionsJson) {}

  /** Enabled delivery options of the store at checkout time; null means disabled. */
  public record Delivery(String pickupInstructions, BigDecimal shippingAmount, String area) {}

  /** Order header as read back for the buyer; buyer contact data is not loaded. */
  public record Order(
      UUID id,
      UUID tenantId,
      long number,
      String status,
      String currency,
      BigDecimal subtotal,
      BigDecimal shipping,
      BigDecimal total,
      FulfillmentType fulfillment,
      String fulfillmentText,
      Instant reservationExpiresAt,
      Instant createdAt,
      String requestFingerprint,
      String accessTokenHash) {}

  /** Item snapshot as shown to the buyer; SKU and cost stay internal. */
  public record Item(
      String productName,
      String optionsJson,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal total) {}

  /** Everything inserted for a new order, already validated and priced by the service. */
  public record NewOrder(
      UUID id,
      CheckoutRecords.Store store,
      long number,
      CheckoutCommand command,
      BigDecimal subtotal,
      BigDecimal shipping,
      BigDecimal total,
      String fulfillmentText,
      Instant createdAt,
      Instant reservationExpiresAt,
      String accessTokenHash) {}
}
