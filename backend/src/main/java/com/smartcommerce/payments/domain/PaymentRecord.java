package com.smartcommerce.payments.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** The stored payment of one sales order (SC-013). */
public record PaymentRecord(
    UUID id, String status, BigDecimal amount, String providerOrderId, String checkoutUrl) {
  public boolean created() {
    return "CREATED".equals(status);
  }
}
