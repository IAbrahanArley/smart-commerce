package com.smartcommerce.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment facts shown to the store OWNER (SC-015). The Orders API reports no fee or net amount, so
 * none is stored or shown as if known (to be decided with the Sprint 5 finance work).
 */
public record PaymentSummary(
    String providerOrderId,
    String status,
    String providerStatus,
    String providerStatusDetail,
    BigDecimal amount,
    BigDecimal paidAmount,
    Instant approvedAt,
    Instant checkedAt,
    String lastCheckError) {}
