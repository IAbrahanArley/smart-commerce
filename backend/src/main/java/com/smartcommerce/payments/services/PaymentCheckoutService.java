package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentRecord;
import com.smartcommerce.payments.domain.ProviderOrder;
import com.smartcommerce.payments.domain.SellerCredentials;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opens the Checkout Pro payment of a pending order (SC-013, ADR-0013 P1, P2, P8, P11).
 *
 * <ul>
 *   <li>The payment row is claimed first, in its own transaction; the provider call runs outside
 *       any transaction or lock and the answer is stored afterwards.
 *   <li>The provider call is idempotent by a key derived from the order, so a retry after a
 *       timeout, or two concurrent attempts, get the same provider order instead of a second one.
 *   <li>A failure leaves the row CREATING and the order pending: the buyer can simply try again.
 *   <li>The token is always the one of the order's own tenant; the tenant comes from the order the
 *       orders module resolved, never from the client.
 * </ul>
 */
@Service
public class PaymentCheckoutService {
  private static final Logger LOG = LoggerFactory.getLogger(PaymentCheckoutService.class);
  private final PaymentRepository payments;
  private final PaymentConnectionService connections;
  private final PaymentProviders providers;
  private final Clock clock;
  private final TransactionTemplate writes;
  private final TransactionTemplate reads;

  public PaymentCheckoutService(
      PaymentRepository payments,
      PaymentConnectionService connections,
      PaymentProviders providers,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.payments = payments;
    this.connections = connections;
    this.providers = providers;
    this.clock = clock;
    this.writes = new TransactionTemplate(transactions);
    this.writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.reads = new TransactionTemplate(transactions);
    this.reads.setReadOnly(true);
  }

  /** Whether this environment has the payment integration configured at all. */
  public boolean enabled() {
    return providers.available();
  }

  /** Whether buyers of {@code tenant} can pay now: integration configured and account connected. */
  public boolean acceptsPayments(UUID tenant) {
    return providers.available()
        && Boolean.TRUE.equals(reads.execute(tx -> payments.connected(tenant)));
  }

  /** Returns the checkout URL to redirect the buyer to, creating the provider order if needed. */
  public String open(CheckoutPaymentRequest request) {
    var client = providers.requireOrders();
    PaymentRecord payment =
        writes.execute(
            tx -> payments.claim(request.tenant(), request.orderId(), request.total(), now()));
    // Order snapshots are immutable, so a stored amount that differs is a bug, never a retry.
    if (payment.amount().compareTo(request.total()) != 0)
      throw new IllegalStateException("Payment amount differs from the order total");
    if (payment.created()) return payment.checkoutUrl();

    SellerCredentials seller = connections.sellerCredentials(request.tenant());
    ProviderOrder created;
    try {
      created =
          client.createCheckoutOrder(
              seller.accessToken(), idempotencyKey(request.orderId()), request, now());
    } catch (PaymentException failure) {
      LOG.warn("Checkout Pro order creation failed for order {}", request.orderId());
      throw failure;
    }
    Instant now = now();
    boolean stored =
        Boolean.TRUE.equals(
            writes.execute(
                tx ->
                    payments.markCreated(
                        request.tenant(),
                        payment.id(),
                        seller.providerUserId(),
                        created.id(),
                        created.status(),
                        created.checkoutUrl(),
                        created.expiresAt(),
                        now)));
    if (stored) return created.checkoutUrl();
    // A concurrent attempt stored first; with the shared idempotency key it is the same order.
    PaymentRecord current =
        reads.execute(tx -> payments.find(request.tenant(), request.orderId()).orElseThrow());
    if (!current.created() || !created.id().equals(current.providerOrderId()))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    return current.checkoutUrl();
  }

  /** One provider order per sales order: the same key on every attempt (ADR-0013 P1). */
  static String idempotencyKey(UUID order) {
    return UUID.nameUUIDFromBytes(("sc-checkout-pro:" + order).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
