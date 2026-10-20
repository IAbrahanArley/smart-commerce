package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.CheckoutCommand;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.CheckoutView;
import com.smartcommerce.orders.repositories.OrderRepository;
import com.smartcommerce.payments.services.PaymentCheckoutService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Public checkout entry point (ADR-0012 §3–§7, SC-008). Deliberately not transactional: each step
 * holds at most one database connection, so concurrent checkouts can never wait on the pool for a
 * second one. Order: resolve the published store; answer a retry of an existing request key without
 * charging the per-IP limit (replay or conflict, nothing is written); charge the per-IP admission
 * in its own committed transaction; then place the order in {@link OrderPlacementService}, which
 * re-checks the key under its lock. Enabled only by configuration (D6). Where the payment
 * integration is configured, a store without a connected account takes no new orders (SC-013): the
 * buyer would have no way to pay. Without the integration (local development, D6 environments)
 * orders are still placed without payment, as before.
 */
@Service
public class OrderCheckoutService {
  private final boolean enabled;
  private final OrderRepository orders;
  private final OrderAdmissionService admission;
  private final OrderPlacementService placement;
  private final PaymentCheckoutService payments;
  private final Clock clock;

  public OrderCheckoutService(
      @Value("${orders.checkout.enabled:false}") boolean enabled,
      OrderRepository orders,
      OrderAdmissionService admission,
      OrderPlacementService placement,
      PaymentCheckoutService payments,
      Clock clock) {
    this.enabled = enabled;
    this.orders = orders;
    this.admission = admission;
    this.placement = placement;
    this.payments = payments;
    this.clock = clock;
  }

  public CheckoutView checkout(String slug, CheckoutCommand command, String clientIp) {
    if (!enabled) throw new OrderException(OrderException.Reason.MISSING);
    CheckoutRecords.Store store =
        orders
            .publishedStore(slug)
            .orElseThrow(() -> new OrderException(OrderException.Reason.MISSING));
    // A committed order for this key is final: retries after an ambiguous result are answered
    // here, so they do not use up the buyer's attempts.
    var previous = orders.byRequest(store.tenantId(), command.requestId());
    if (previous.isPresent()) {
      if (!previous.get().requestFingerprint().equals(command.fingerprint()))
        throw new OrderException(OrderException.Reason.REQUEST_CONFLICT);
      CheckoutRecords.Order order = previous.get();
      return new CheckoutView(
          OrderViews.view(
              order,
              orders.items(order.tenantId(), order.id()),
              ReservationPolicy.now(clock),
              payments.acceptsPayments(order.tenantId())),
          null,
          true);
    }
    if (payments.enabled() && !payments.acceptsPayments(store.tenantId()))
      throw new OrderException(OrderException.Reason.PAYMENT_UNAVAILABLE);
    admission.admit(clientIp);
    return placement.place(store, command);
  }
}
