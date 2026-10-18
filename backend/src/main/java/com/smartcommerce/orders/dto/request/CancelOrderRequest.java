package com.smartcommerce.orders.dto.request;

/** Store OWNER cancellation body; the reason is validated by {@code CancelReason}. */
public record CancelOrderRequest(String reason) {}
