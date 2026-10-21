package com.smartcommerce.payments.domain;

/**
 * Routing data of an authenticated provider notification (SC-014). It only says which provider
 * order to look at; nothing in it is trusted as a payment state.
 */
public record PaymentNotification(
    String eventKey, String topic, String action, String providerOrderId, String providerUserId) {}
