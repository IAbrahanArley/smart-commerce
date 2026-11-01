package com.smartcommerce.finance.dto.request;

/** A signed balance correction (SC-050): amount as a decimal string, never a float. */
public record AdjustmentRequest(
    String requestId, String amount, String occurredOn, String reason) {}
