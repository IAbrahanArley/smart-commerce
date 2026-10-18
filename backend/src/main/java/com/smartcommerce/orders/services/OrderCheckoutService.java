package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.CheckoutCommand;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.CheckoutView;
import com.smartcommerce.orders.repositories.OrderRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Public checkout entry point (ADR-0012 §3–§7, SC-008). Deliberately not transactional: each step
 * holds at most one database connection, so concurrent checkouts can never wait on the pool for a
 * second one. Order: resolve the published store; answer a retry of an existing request key without
 * charging the per-IP limit (replay or conflict, nothing is written); charge the per-IP admission
 * in its own committed transaction; then place the order in {@link OrderPlacementService}, which
 * re-checks the key under its lock. Enabled only by configuration until payments exist (D6).
 */
@Service
public class OrderCheckoutService {
  private final boolean enabled;
  private final OrderRepository orders;
  private final OrderAdmissionService admission;
  private final OrderPlacementService placement;
  private final Clock clock;

  public OrderCheckoutService(
      @Value("${orders.checkout.enabled:false}") boolean enabled,
      OrderRepository orders,
      OrderAdmissionService admission,
      OrderPlacementService placement,
      Clock clock) {
    this.enabled = enabled;
    this.orders = orders;
    this.admission = admission;
    this.placement = placement;
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
              order, orders.items(order.tenantId(), order.id()), ReservationPolicy.now(clock)),
          null,
          true);
    }
    admission.admit(clientIp);
    return placement.place(store, command);
  }
}
