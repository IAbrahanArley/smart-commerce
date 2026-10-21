package com.smartcommerce.payments.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** The stored payment of one sales order (SC-013, SC-014). */
public record PaymentRecord(
    UUID id,
    UUID orderId,
    String status,
    BigDecimal amount,
    String providerOrderId,
    String providerUserId,
    String checkoutUrl) {
  /** The provider order exists (CREATED, APPROVED or REFUNDED). */
  public boolean created() {
    return "CREATED".equals(status) || approved() || refunded();
  }

  public boolean refunded() {
    return "REFUNDED".equals(status);
  }

  public boolean approved() {
    return "APPROVED".equals(status);
  }
}
