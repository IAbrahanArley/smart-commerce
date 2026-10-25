package com.smartcommerce.payments.domain;

import java.time.Instant;

/**
 * A Checkout Pro preference as answered by the provider, already validated by the client; its id
 * takes the place of the provider order id (ADR-0013 P1 change).
 */
public record ProviderOrder(String id, String status, String checkoutUrl, Instant expiresAt) {}
