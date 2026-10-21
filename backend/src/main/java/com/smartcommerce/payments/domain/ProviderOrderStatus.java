package com.smartcommerce.payments.domain;

import java.math.BigDecimal;

/**
 * A provider order as read back by the backend (SC-014, ADR-0013 P5): the only source of truth for
 * confirming a payment. {@code userId} is the seller account when the provider reports it.
 */
public record ProviderOrderStatus(
    String id,
    String status,
    String statusDetail,
    BigDecimal totalAmount,
    BigDecimal totalPaidAmount,
    String externalReference,
    String userId,
    String currency) {
  /**
   * Paid in full: processed and accredited, with the whole amount paid. Hypothesis to confirm in
   * the sandbox (SC-014 validation): other combinations are recorded but never confirm a payment.
   */
  public boolean paidInFull(BigDecimal expected) {
    return "processed".equals(status)
        && "accredited".equals(statusDetail)
        && totalPaidAmount != null
        && totalPaidAmount.compareTo(expected) == 0;
  }
}
