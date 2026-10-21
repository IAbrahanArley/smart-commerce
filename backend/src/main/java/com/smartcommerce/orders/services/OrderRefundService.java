package com.smartcommerce.orders.services;

import com.smartcommerce.orders.domain.CancelReason;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.AdminOrderView;
import com.smartcommerce.orders.repositories.OrderAdminRepository;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.services.PaymentRefundService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Full refund by the store OWNER (SC-016, ADR-0013 P9). The OWNER and the PAID state are checked in
 * a short transaction of their own; the provider is called by the payments module outside any order
 * lock, and the order becomes REFUNDED only when the provider confirms it. A refunded order is
 * returned unchanged, so a repeat is harmless.
 */
@Service
public class OrderRefundService {
  private final OrderAdminRepository orders;
  private final OrderAdminService admin;
  private final PaymentRefundService refunds;
  private final TransactionTemplate writes;

  public OrderRefundService(
      OrderAdminRepository orders,
      OrderAdminService admin,
      PaymentRefundService refunds,
      PlatformTransactionManager transactions) {
    this.orders = orders;
    this.admin = admin;
    this.refunds = refunds;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public AdminOrderView refund(
      UUID actor, UUID tenant, UUID order, CancelReason reason, boolean restock) {
    String status =
        writes.execute(
            tx ->
                orders
                    .lockOwned(actor, tenant, order)
                    .orElseThrow(() -> new OrderException(OrderException.Reason.MISSING))
                    .status());
    if ("REFUNDED".equals(status)) return admin.find(actor, tenant, order);
    if (!"PAID".equals(status)) throw new OrderException(OrderException.Reason.NOT_REFUNDABLE);
    try {
      refunds.request(tenant, order, actor, reason.text(), restock);
    } catch (PaymentException failure) {
      throw new OrderException(
          switch (failure.reason()) {
            case NOT_REFUNDABLE -> OrderException.Reason.NOT_REFUNDABLE;
            case REFUND_CONFLICT -> OrderException.Reason.REFUND_CONFLICT;
            case UNAVAILABLE, NOT_CONNECTED, REVOKED, ACCOUNT_CHANGED ->
                OrderException.Reason.PAYMENT_UNAVAILABLE;
            default -> OrderException.Reason.PAYMENT_FAILED;
          });
    }
    return admin.find(actor, tenant, order);
  }
}
