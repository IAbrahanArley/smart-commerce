package com.smartcommerce.payments.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the orders module hands over to open a Checkout Pro payment (SC-013): the order's own
 * snapshot, already validated and priced by the backend. No buyer data is included (P11).
 */
public record CheckoutPaymentRequest(
    UUID tenant, String storeSlug, UUID orderId, BigDecimal total, List<Line> lines) {
  public CheckoutPaymentRequest {
    lines = List.copyOf(lines);
    BigDecimal sum =
        lines.stream()
            .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (lines.isEmpty() || total.signum() <= 0 || sum.compareTo(total) != 0)
      throw new IllegalArgumentException("Payment lines must add up to the order total");
  }

  /** One provider item: a product line or the shipping fee. */
  public record Line(String title, BigDecimal unitPrice, int quantity) {}
}
