package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.repositories.OrderPaymentRepository;
import com.smartcommerce.payments.services.PaymentApprovalListener;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a confirmed payment into a paid order (SC-014, ADR-0013 P4, P7, P8). Runs inside the
 * payments transaction that marked the payment APPROVED, so everything commits together. Lock
 * order: payment, then this order row, then stock balances in variant order.
 *
 * <ul>
 *   <li>Already PAID: nothing happens (idempotent).
 *   <li>PENDING_PAYMENT, EXPIRED or CANCELLED: the stock is deducted once, by the system actor,
 *       from the order's reservation or, if that ended, from what is available now. If it cannot be
 *       covered the order is still PAID, flagged {@code paid_without_stock} for the store to decide
 *       (P4: never an automatic refund).
 * </ul>
 */
@Service
public class OrderPaymentApproval implements PaymentApprovalListener {
  private static final Logger LOG = LoggerFactory.getLogger(OrderPaymentApproval.class);
  private final OrderPaymentRepository orders;
  private final InventoryReservationService inventory;

  public OrderPaymentApproval(
      OrderPaymentRepository orders, InventoryReservationService inventory) {
    this.orders = orders;
    this.inventory = inventory;
  }

  @Override
  public void approved(UUID tenant, UUID order, Instant paidAt) {
    var locked =
        orders
            .lock(tenant, order)
            .orElseThrow(() -> new IllegalStateException("Payment without order"));
    if ("PAID".equals(locked.status())) return;
    // Only possible months after an expiry (D2). Rolls the approval back: the event ends FAILED
    // and the payment stays CREATED, visible to reconciliation (SC-015).
    if (locked.anonymized()) throw new IllegalStateException("Payment for an anonymized order");
    var sale =
        inventory.sell(
            tenant, order, orders.lines(tenant, order), "Venda do pedido nº " + locked.number());
    if (!orders.markPaid(tenant, order, !sale.deducted(), paidAt))
      throw new IllegalStateException("Order changed while locked");
    if (!sale.deducted()) LOG.warn("Order {} paid without stock: the store must decide", order);
  }
}
