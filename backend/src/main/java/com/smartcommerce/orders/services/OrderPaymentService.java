package com.smartcommerce.orders.services;

import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.orders.domain.AccessToken;
import com.smartcommerce.orders.domain.CheckoutRecords;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.repositories.OrderRepository;
import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.services.PaymentCheckoutService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Buyer payment start (SC-013): the same checks as the status read (store of the slug, token
 * matching the stored hash, one 404 otherwise), then only a pending order inside its reservation
 * window is handed to the payments module, priced from its own snapshots. The read holds one
 * connection and ends before the provider is called.
 */
@Service
public class OrderPaymentService {
  private final OrderRepository orders;
  private final PaymentCheckoutService payments;
  private final Clock clock;
  private final TransactionTemplate reads;

  public OrderPaymentService(
      OrderRepository orders,
      PaymentCheckoutService payments,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.orders = orders;
    this.payments = payments;
    this.clock = clock;
    this.reads = new TransactionTemplate(transactions);
    this.reads.setReadOnly(true);
    this.reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  /** Returns the provider checkout URL for the buyer of {@code orderId}. */
  public String start(String slug, UUID orderId, String token) {
    CheckoutPaymentRequest request = reads.execute(tx -> request(slug, orderId, token));
    try {
      return payments.open(request);
    } catch (PaymentException failure) {
      throw new OrderException(
          switch (failure.reason()) {
            case UNAVAILABLE, NOT_CONNECTED, REVOKED -> OrderException.Reason.PAYMENT_UNAVAILABLE;
            default -> OrderException.Reason.PAYMENT_FAILED;
          });
    }
  }

  private CheckoutPaymentRequest request(String slug, UUID orderId, String token) {
    CheckoutRecords.Order order =
        orders
            .forBuyer(slug, orderId)
            .filter(found -> AccessToken.matches(token, found.accessTokenHash()))
            .orElseThrow(() -> new OrderException(OrderException.Reason.MISSING));
    if (!"PENDING_PAYMENT".equals(order.status())
        || !ReservationPolicy.active(order.reservationExpiresAt(), ReservationPolicy.now(clock)))
      throw new OrderException(OrderException.Reason.NOT_PAYABLE);
    if (!payments.acceptsPayments(order.tenantId()))
      throw new OrderException(OrderException.Reason.PAYMENT_UNAVAILABLE);
    List<CheckoutPaymentRequest.Line> lines = new ArrayList<>();
    for (CheckoutRecords.Item item : orders.items(order.tenantId(), order.id()))
      lines.add(new CheckoutPaymentRequest.Line(title(item), item.unitPrice(), item.quantity()));
    if (order.shipping().signum() > 0)
      lines.add(new CheckoutPaymentRequest.Line("Frete", order.shipping(), 1));
    return new CheckoutPaymentRequest(order.tenantId(), slug, order.id(), order.total(), lines);
  }

  private static String title(CheckoutRecords.Item item) {
    var options = VariantValues.decode(item.optionsJson());
    if (options.isEmpty()) return item.productName();
    return item.productName()
        + " ("
        + options.stream()
            .map(option -> option.name() + ": " + option.value())
            .collect(Collectors.joining(", "))
        + ")";
  }
}
