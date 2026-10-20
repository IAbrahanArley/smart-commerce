package com.smartcommerce.payments.domain;

import java.time.Instant;

/** A Checkout Pro order as answered by the provider, already validated by the client. */
public record ProviderOrder(String id, String status, String checkoutUrl, Instant expiresAt) {}
