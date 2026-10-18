package com.smartcommerce.orders.dto.response;

/**
 * Checkout result. {@code accessToken} is returned only when the order is created; an idempotent
 * replay ({@code replayed=true}) never re-issues it, so the storefront keeps the first one.
 */
public record CheckoutView(OrderView order, String accessToken, boolean replayed) {
  @Override
  public String toString() {
    return "CheckoutView[order=" + order.id() + ", replayed=" + replayed + "]";
  }
}
