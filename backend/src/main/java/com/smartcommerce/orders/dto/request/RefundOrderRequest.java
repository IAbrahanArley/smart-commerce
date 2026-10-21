package com.smartcommerce.orders.dto.request;

/**
 * Store OWNER full refund body (SC-016). {@code reason} follows the cancellation rule (one line, 3
 * to 300 characters); {@code restock} is mandatory, so returning units to stock is always an
 * explicit choice.
 */
public record RefundOrderRequest(String reason, Boolean restock) {}
