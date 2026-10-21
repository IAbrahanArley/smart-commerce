package com.smartcommerce.payments.services;

import java.time.Instant;
import java.util.UUID;

/**
 * Called inside the transaction that settles a payment, so the order transition and the stock
 * movements commit or roll back with it. Implemented by the orders module: payments never depends
 * on orders.
 */
public interface PaymentListener {
  /** The payment was confirmed paid (SC-014): mark the order PAID and deduct its stock once. */
  void approved(UUID tenant, UUID order, Instant paidAt);

  /**
   * The provider confirmed the full refund (SC-016): mark the order REFUNDED and, only when the
   * store chose it, return its units to stock.
   */
  void refunded(UUID tenant, UUID order, boolean restock, Instant refundedAt);
}
