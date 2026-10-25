package com.smartcommerce.payments.domain;

/**
 * Routing data of an authenticated provider notification (SC-014). It only says which provider
 * payment to look at ({@code providerOrderId} holds the notified payment id, ADR-0013 P1 change);
 * nothing in it is trusted as a payment state.
 */
public record PaymentNotification(
    String eventKey, String topic, String action, String providerOrderId, String providerUserId) {}
