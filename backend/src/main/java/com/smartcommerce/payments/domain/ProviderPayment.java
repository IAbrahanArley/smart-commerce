package com.smartcommerce.payments.domain;

import java.math.BigDecimal;

/**
 * A Mercado Pago payment as read with the seller's token ({@code GET /v1/payments/{id}} or the
 * search by {@code external_reference}), already validated by the client. Nothing here is trusted
 * before the service checks reference, seller, currency and amount against the stored payment.
 */
public record ProviderPayment(
    String id,
    String status,
    String statusDetail,
    BigDecimal amount,
    BigDecimal refundedAmount,
    String externalReference,
    String collectorId,
    String currency) {
  /** Approved for the whole expected amount. */
  public boolean paidInFull(BigDecimal expected) {
    return "approved".equals(status) && amount != null && amount.compareTo(expected) == 0;
  }

  /** Fully refunded (P9): status {@code refunded}, or the refunded amount covers the payment. */
  public boolean refunded() {
    return "refunded".equals(status)
        || (refundedAmount != null
            && amount != null
            && refundedAmount.compareTo(amount) >= 0
            && amount.signum() > 0);
  }
}
