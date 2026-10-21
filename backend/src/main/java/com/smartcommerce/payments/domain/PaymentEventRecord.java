package com.smartcommerce.payments.domain;

import java.util.UUID;

/** A stored notification claimed for processing. */
public record PaymentEventRecord(UUID id, UUID tenant, String providerOrderId, int attempts) {}
