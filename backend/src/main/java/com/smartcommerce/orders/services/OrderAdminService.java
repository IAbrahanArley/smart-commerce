package com.smartcommerce.orders.services;

import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.domain.AdminOrderQuery;
import com.smartcommerce.orders.domain.AdminOrderRecords;
import com.smartcommerce.orders.domain.CancelReason;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.AdminOrderPage;
import com.smartcommerce.orders.dto.response.AdminOrderView;
import com.smartcommerce.orders.dto.response.OrderView;
import com.smartcommerce.orders.repositories.OrderAdminRepository;
import com.smartcommerce.payments.services.PaymentCheckoutService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders for the store OWNER (SC-010): list, detail and cancellation. The tenant comes from the
 * route but is only honoured through the actor's OWNER membership; anything else is one 404.
 */
@Service
public class OrderAdminService {
  private final OrderAdminRepository orders;
  private final InventoryReservationService reservations;
  private final PaymentCheckoutService payments;
  private final Clock clock;

  public OrderAdminService(
      OrderAdminRepository orders,
      InventoryReservationService reservations,
      PaymentCheckoutService payments,
      Clock clock) {
    this.orders = orders;
    this.reservations = reservations;
    this.payments = payments;
    this.clock = clock;
  }

  /** Orders created in {@code [from, to)} by effective status (SC-025 advisor); OWNER only. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Long> countsByStatus(UUID actor, UUID tenant, Instant from, Instant to) {
    if (!orders.ownsStore(actor, tenant)) throw missing();
    return orders.countsByStatus(tenant, from, to, ReservationPolicy.now(clock));
  }

  /** Pending orders whose reservation ends within {@code within} (SC-024 alert); OWNER only. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public long expiringSoon(UUID actor, UUID tenant, Duration within) {
    if (!orders.ownsStore(actor, tenant)) throw missing();
    Instant now = ReservationPolicy.now(clock);
    return orders.pendingExpiring(tenant, now, now.plus(within));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AdminOrderPage list(UUID actor, UUID tenant, AdminOrderQuery query) {
    if (!orders.ownsStore(actor, tenant)) throw missing();
    var rows = orders.list(actor, tenant, query, ReservationPolicy.now(clock));
    boolean more = rows.size() > AdminOrderQuery.PAGE_SIZE;
    var page = more ? rows.subList(0, AdminOrderQuery.PAGE_SIZE) : rows;
    return new AdminOrderPage(
        page.stream()
            .map(
                row ->
                    new AdminOrderPage.Summary(
                        row.id(),
                        row.number(),
                        row.status(),
                        OrderViews.money(row.total()),
                        "BRL",
                        row.buyerName(),
                        row.fulfillment().name(),
                        row.units(),
                        row.createdAt(),
                        row.reservationExpiresAt()))
            .toList(),
        more ? String.valueOf(page.getLast().number()) : null);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AdminOrderView find(UUID actor, UUID tenant, UUID order) {
    return view(actor, tenant, order, ReservationPolicy.now(clock));
  }

  /**
   * Cancels a pending order inside its deadline and releases its reservations in the same
   * transaction. Cancelling a cancelled order returns it unchanged (no second release or new
   * reason); an order past its deadline is rejected with ORDER_EXPIRED.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AdminOrderView cancel(UUID actor, UUID tenant, UUID order, CancelReason reason) {
    Instant now = ReservationPolicy.now(clock);
    AdminOrderRecords.Owned owned =
        orders.lockOwned(actor, tenant, order).orElseThrow(OrderAdminService::missing);
    if ("CANCELLED".equals(owned.status())) return view(actor, tenant, order, now);
    if (!"PENDING_PAYMENT".equals(owned.status())
        || !ReservationPolicy.active(owned.reservationExpiresAt(), now)
        || !orders.cancel(owned.tenantId(), owned.id(), actor, reason.text(), now))
      throw new OrderException(OrderException.Reason.ORDER_EXPIRED);
    reservations.release(owned.tenantId(), owned.id());
    return view(actor, tenant, order, now);
  }

  private AdminOrderView view(UUID actor, UUID tenant, UUID id, Instant now) {
    AdminOrderRecords.Detail order =
        orders.detail(actor, tenant, id, now).orElseThrow(OrderAdminService::missing);
    List<AdminOrderView.Item> items =
        orders.items(tenant, id).stream()
            .map(
                item ->
                    new AdminOrderView.Item(
                        item.productName(),
                        VariantValues.decode(item.optionsJson()),
                        item.sku(),
                        OrderViews.money(item.unitPrice()),
                        item.unitCost() == null ? null : OrderViews.money(item.unitCost()),
                        item.quantity(),
                        OrderViews.money(item.total())))
            .toList();
    return new AdminOrderView(
        order.id(),
        order.number(),
        order.status(),
        "BRL",
        items,
        OrderViews.money(order.subtotal()),
        OrderViews.money(order.shipping()),
        OrderViews.money(order.total()),
        new OrderView.Fulfillment(order.fulfillment().name(), order.fulfillmentText()),
        order.anonymizedAt() == null
            ? new AdminOrderView.Buyer(order.buyerName(), order.buyerEmail(), order.buyerPhone())
            : null,
        order.line1() == null
            ? null
            : new AdminOrderView.Address(
                order.line1(),
                order.line2(),
                order.district(),
                order.city(),
                order.state(),
                order.postalCode()),
        order.reservationExpiresAt(),
        order.createdAt(),
        order.expiredAt(),
        order.cancelledAt() == null
            ? null
            : new AdminOrderView.Cancellation(order.cancelReason(), order.cancelledAt()),
        order.anonymizedAt(),
        order.paidAt(),
        order.paidWithoutStock(),
        order.refundedAt(),
        payments
            .summary(tenant, id)
            .map(
                payment ->
                    new AdminOrderView.Payment(
                        "MERCADO_PAGO",
                        payment.providerOrderId(),
                        payment.status(),
                        payment.providerStatus(),
                        payment.providerStatusDetail(),
                        OrderViews.money(payment.amount()),
                        payment.paidAmount() == null
                            ? null
                            : OrderViews.money(payment.paidAmount()),
                        payment.approvedAt(),
                        payment.checkedAt(),
                        payment.lastCheckError(),
                        payment.refund() == null
                            ? null
                            : new AdminOrderView.Refund(
                                payment.refund().status(),
                                payment.refund().reason(),
                                payment.refund().restock(),
                                payment.refund().requestedAt(),
                                payment.refund().providerAcceptedAt(),
                                payment.refund().confirmedAt(),
                                payment.refund().lastError())))
            .orElse(null));
  }

  private static OrderException missing() {
    return new OrderException(OrderException.Reason.MISSING);
  }
}
