package com.smartcommerce.payments.services;

import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.PaymentRecord;
import com.smartcommerce.payments.domain.PaymentSummary;
import com.smartcommerce.payments.domain.ProviderOrder;
import com.smartcommerce.payments.domain.SellerCredentials;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.repositories.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
 *       timeout, or two concurrent attempts, reuse the provider's answer where it honours the key;
 *       otherwise the first stored preference wins and only that one is shown to the buyer.
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

  /** The payment of {@code order} as the store OWNER sees it (SC-015); tenant already trusted. */
  public Optional<PaymentSummary> summary(UUID tenant, UUID order) {
    return reads.execute(tx -> payments.summary(tenant, order));
  }

  /** Returns the checkout URL to redirect the buyer to, creating the preference if needed. */
  public String open(CheckoutPaymentRequest request) {
    var client = providers.requireCheckout();
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
          client.createPreference(
              seller.accessToken(),
              idempotencyKey(request.orderId()),
              request,
              now(),
              reads.execute(tx -> connections.maxInstallments(request.tenant())));
    } catch (PaymentException failure) {
      LOG.warn("Checkout Pro preference creation failed for order {}", request.orderId());
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
    // A concurrent attempt stored first. Its preference has the same order reference, so the buyer
    // goes to the stored one and the other simply expires unpaid.
    PaymentRecord current =
        reads.execute(tx -> payments.find(request.tenant(), request.orderId()).orElseThrow());
    if (!current.created()) throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    return current.checkoutUrl();
  }

  /** One preference per sales order: the same key on every attempt (ADR-0013 P1). */
  static String idempotencyKey(UUID order) {
    return UUID.nameUUIDFromBytes(("sc-checkout-pro:" + order).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
