package com.smartcommerce.payments.domain;

import java.util.UUID;

/**
 * A claimed notification. For the {@code payment} topic (preferences, ADR-0013 P1 change) {@code
 * providerOrderId} holds the notified payment id and {@code providerUserId} the seller.
 */
public record PaymentEventRecord(
    UUID id,
    UUID tenant,
    String providerOrderId,
    int attempts,
    String topic,
    String providerUserId) {}
