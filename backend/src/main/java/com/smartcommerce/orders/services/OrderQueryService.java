package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.AccessToken;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.OrderView;
import com.smartcommerce.orders.repositories.OrderRepository;
import com.smartcommerce.payments.services.PaymentCheckoutService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buyer status read (ADR-0012 §3): the order must belong to the store of the slug and the presented
 * token must match the stored hash. Absent, foreign or wrong-token orders are one 404.
 */
@Service
public class OrderQueryService {
  private final OrderRepository orders;
  private final PaymentCheckoutService payments;
  private final Clock clock;

  public OrderQueryService(OrderRepository orders, PaymentCheckoutService payments, Clock clock) {
    this.orders = orders;
    this.payments = payments;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OrderView find(String slug, UUID orderId, String token) {
    CheckoutRecords.Order order =
        orders
            .forBuyer(slug, orderId)
            .filter(found -> AccessToken.matches(token, found.accessTokenHash()))
            .orElseThrow(() -> new OrderException(OrderException.Reason.MISSING));
    return OrderViews.view(
        order,
        orders.items(order.tenantId(), order.id()),
        ReservationPolicy.now(clock),
        payments.acceptsPayments(order.tenantId()));
  }
}
