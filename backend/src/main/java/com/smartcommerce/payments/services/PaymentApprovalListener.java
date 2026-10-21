package com.smartcommerce.payments.services;

import java.time.Instant;
import java.util.UUID;

/**
 * Called inside the transaction that marks a payment APPROVED (SC-014), so the order transition and
 * the stock deduction commit or roll back with it. Implemented by the orders module: payments never
 * depends on orders.
 */
public interface PaymentApprovalListener {
  void approved(UUID tenant, UUID order, Instant paidAt);
}
