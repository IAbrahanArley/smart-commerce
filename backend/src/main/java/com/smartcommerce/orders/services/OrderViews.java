package com.smartcommerce.orders.services;

import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.dto.response.OrderView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/** Maps persisted snapshots to the buyer view; money keeps the textual two-decimal contract. */
final class OrderViews {
  private OrderViews() {}

  static OrderView view(
      CheckoutRecords.Order order, List<CheckoutRecords.Item> items, Instant now) {
    String status =
        "PENDING_PAYMENT".equals(order.status())
                && !ReservationPolicy.active(order.reservationExpiresAt(), now)
            ? "EXPIRED"
            : order.status();
    return new OrderView(
        order.id(),
        order.number(),
        status,
        order.currency(),
        items.stream()
            .map(
                item ->
                    new OrderView.Item(
                        item.productName(),
                        VariantValues.decode(item.optionsJson()),
                        money(item.unitPrice()),
                        item.quantity(),
                        money(item.total())))
            .toList(),
        money(order.subtotal()),
        money(order.shipping()),
        money(order.total()),
        new OrderView.Fulfillment(order.fulfillment().name(), order.fulfillmentText()),
        order.reservationExpiresAt(),
        order.createdAt());
  }

  static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
