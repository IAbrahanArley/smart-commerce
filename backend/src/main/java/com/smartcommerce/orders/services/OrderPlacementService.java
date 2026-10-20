package com.smartcommerce.orders.services;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.domain.ReservationResult;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.domain.AccessToken;
import com.smartcommerce.orders.domain.CheckoutCommand;
import com.smartcommerce.orders.domain.CheckoutLine;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.domain.FulfillmentType;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.CheckoutView;
import com.smartcommerce.orders.repositories.OrderRepository;
import com.smartcommerce.payments.services.PaymentCheckoutService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional part of the public checkout (ADR-0012 §3–§7, SC-008). In one READ COMMITTED
 * transaction: serialize on the request key and replay or reject it, enforce the pending limit,
 * revalidate every variant against the store, price everything in the backend, confirm the total
 * the buyer saw, then insert the order, its snapshots and its reservations. Any failure rolls back
 * all of it, so a rejected attempt never consumes the request key. Called only by {@link
 * OrderCheckoutService}, after the per-IP admission has committed on its own connection.
 */
@Service
public class OrderPlacementService {
  static final int MAX_PENDING_PER_EMAIL = 5;
  private static final String CURRENCY = "BRL";
  private final OrderRepository orders;
  private final InventoryReservationService reservations;
  private final PaymentCheckoutService payments;
  private final Clock clock;
  private final Duration reservationTtl;

  public OrderPlacementService(
      OrderRepository orders,
      InventoryReservationService reservations,
      PaymentCheckoutService payments,
      Clock clock,
      @Value("${inventory.reservation.ttl:PT30M}") Duration reservationTtl) {
    this.orders = orders;
    this.reservations = reservations;
    this.payments = payments;
    this.clock = clock;
    this.reservationTtl = new ReservationPolicy(reservationTtl).ttl();
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CheckoutView place(CheckoutRecords.Store store, CheckoutCommand command) {
    orders.lockRequest(store.tenantId(), command.requestId());
    var previous = orders.byRequest(store.tenantId(), command.requestId());
    if (previous.isPresent()) {
      if (!previous.get().requestFingerprint().equals(command.fingerprint()))
        throw new OrderException(OrderException.Reason.REQUEST_CONFLICT);
      return new CheckoutView(view(previous.get()), null, true);
    }

    Instant now = ReservationPolicy.now(clock);
    orders.lockBuyer(store.storeId(), command.buyer().emailKey());
    if (orders.pendingCount(store.storeId(), command.buyer().emailKey(), now)
        >= MAX_PENDING_PER_EMAIL) throw new OrderException(OrderException.Reason.PENDING_LIMIT);

    Map<UUID, CheckoutRecords.Variant> variants =
        orders.variants(store, command.variantIds()).stream()
            .collect(Collectors.toMap(CheckoutRecords.Variant::id, Function.identity()));
    if (variants.size() != command.lines().size()
        || variants.values().stream().anyMatch(variant -> !CURRENCY.equals(variant.currency())))
      throw new OrderException(OrderException.Reason.ITEM_UNAVAILABLE);

    CheckoutRecords.Delivery delivery = orders.delivery(store);
    BigDecimal shipping;
    String fulfillmentText;
    if (command.fulfillment() == FulfillmentType.PICKUP) {
      if (delivery.pickupInstructions() == null)
        throw new OrderException(OrderException.Reason.FULFILLMENT_UNAVAILABLE);
      shipping = BigDecimal.ZERO.setScale(2);
      fulfillmentText = delivery.pickupInstructions();
    } else {
      if (delivery.shippingAmount() == null)
        throw new OrderException(OrderException.Reason.FULFILLMENT_UNAVAILABLE);
      shipping = delivery.shippingAmount();
      fulfillmentText = delivery.area();
    }

    BigDecimal subtotal =
        command.lines().stream()
            .map(
                line ->
                    variants
                        .get(line.variantId())
                        .price()
                        .multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal total = subtotal.add(shipping);
    if (total.compareTo(command.expectedTotal()) != 0)
      throw new OrderException(OrderException.Reason.PRICE_CHANGED, total);

    UUID id = UUID.randomUUID();
    AccessToken token = AccessToken.generate();
    orders.insert(
        new CheckoutRecords.NewOrder(
            id,
            store,
            orders.nextNumber(store),
            command,
            subtotal,
            shipping,
            total,
            fulfillmentText,
            now,
            now.plus(reservationTtl),
            token.hash()));
    orders.insertItems(store.tenantId(), id, command.lines(), variants);
    ReservationResult reserved;
    try {
      reserved = reservations.reserve(store.tenantId(), id, reservationLines(command.lines()));
    } catch (InventoryException failure) {
      throw new OrderException(
          failure.reason() == InventoryException.Reason.INSUFFICIENT_STOCK
              ? OrderException.Reason.INSUFFICIENT_STOCK
              : OrderException.Reason.ITEM_UNAVAILABLE);
    }
    orders.updateReservationExpiry(store.tenantId(), id, reserved.expiresAt());
    CheckoutRecords.Order created =
        orders
            .byRequest(store.tenantId(), command.requestId())
            .orElseThrow(() -> new IllegalStateException("Order not persisted"));
    return new CheckoutView(view(created), token.value(), false);
  }

  private com.smartcommerce.orders.dto.response.OrderView view(CheckoutRecords.Order order) {
    return OrderViews.view(
        order,
        orders.items(order.tenantId(), order.id()),
        ReservationPolicy.now(clock),
        payments.acceptsPayments(order.tenantId()));
  }

  private static List<ReservationLine> reservationLines(List<CheckoutLine> lines) {
    return lines.stream()
        .map(line -> new ReservationLine(line.variantId(), line.quantity()))
        .toList();
  }
}
