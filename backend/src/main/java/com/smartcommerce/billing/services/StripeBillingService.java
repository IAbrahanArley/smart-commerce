package com.smartcommerce.billing.services;

import com.smartcommerce.billing.domain.BillingCycle;
import com.smartcommerce.billing.domain.BillingException;
import com.smartcommerce.billing.domain.BillingStatus;
import com.smartcommerce.billing.domain.Plan;
import com.smartcommerce.billing.provider.StripeGateway;
import com.smartcommerce.billing.provider.StripeUnavailableException;
import com.smartcommerce.billing.repositories.BillingRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The paid side of the platform subscription over Stripe (SC-032, ADR-0017 B3–B6). The owner pays
 * on Stripe's hosted Checkout and manages the card and receipts on the customer portal; plan, cycle
 * and cancellation are handled by {@link SubscriptionChangeService} (B13). The panel never sees
 * card data. The account only changes on a signed webhook, and then from the subscription that the
 * Stripe API reports at that moment, never from the event body or the browser's return:
 * redeliveries, out-of-order events and a forged return change nothing.
 */
@Service
public class StripeBillingService {
  private static final Logger LOG = LoggerFactory.getLogger(StripeBillingService.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final long SIGNATURE_TOLERANCE_SECONDS = 300;
  // Events that can change a subscription's standing; any other type is acknowledged and ignored.
  private static final Set<String> HANDLED =
      Set.of(
          "checkout.session.completed",
          "customer.subscription.created",
          "customer.subscription.updated",
          "customer.subscription.deleted",
          "customer.subscription.paused",
          "customer.subscription.resumed",
          "invoice.paid",
          "invoice.payment_failed");

  private final BillingService billing;
  private final BillingRepository accounts;
  private final StripeGateway stripe;
  private final Clock clock;
  private final String webhookSecret;
  private final String origin;

  public StripeBillingService(
      BillingService billing,
      BillingRepository accounts,
      StripeGateway stripe,
      Clock clock,
      @Value("${billing.stripe.webhook-secret:}") String webhookSecret,
      @Value("${identity.public-origin:http://127.0.0.1:3000}") String origin) {
    this.billing = billing;
    this.accounts = accounts;
    this.stripe = stripe;
    this.clock = clock;
    this.webhookSecret = webhookSecret;
    this.origin = origin;
  }

  /**
   * A Checkout page for the chosen plan and cycle. Refused while a subscription is running, even
   * one whose webhook has not arrived yet: Stripe is asked, so a second click cannot subscribe
   * twice. A canceled subscription can be replaced by a new one.
   */
  @Transactional
  public String checkout(UUID owner, Plan plan, BillingCycle cycle) {
    requireEnabled();
    accounts.lockOwner(owner);
    billing.account(owner); // opens the trial if this is the owner's first visit
    String customer = accounts.stripeCustomer(owner).orElse(null);
    if (customer != null
        && stripe
            .currentSubscription(customer)
            .filter(subscription -> !"canceled".equals(subscription.status()))
            .isPresent())
      throw new BillingException(
          BillingException.Reason.SUBSCRIPTION_EXISTS,
          "Você já tem uma assinatura. Para mudar de plano ou de ciclo, escolha o novo plano"
              + " nesta página.");
    String price =
        stripe
            .priceId(BillingCycle.lookupKey(plan, cycle))
            .orElseThrow(
                () -> {
                  LOG.error("Stripe has no active price for a plan of the catalog");
                  return new StripeUnavailableException(null);
                });
    if (customer == null) {
      customer = stripe.createCustomer(owner, accounts.email(owner).orElseThrow());
      accounts.setStripeCustomer(owner, customer, clock.instant());
    }
    return stripe.checkoutUrl(
        customer,
        price,
        owner,
        origin + "/conta/plano?checkout=sucesso",
        origin + "/conta/plano?checkout=cancelado");
  }

  /** The customer portal, for an owner who has gone through Checkout at least once. */
  @Transactional(readOnly = true)
  public String portal(UUID owner) {
    requireEnabled();
    String customer =
        accounts
            .stripeCustomer(owner)
            .orElseThrow(
                () ->
                    new BillingException(
                        BillingException.Reason.NO_SUBSCRIPTION,
                        "Você ainda não tem assinatura. Escolha um plano para assinar."));
    return stripe.portalUrl(customer, origin + "/conta/plano");
  }

  /**
   * A webhook delivery: the signature is checked over the raw body, the event id makes it
   * idempotent, and the owner's account is reconciled from the API. A Stripe failure rolls back the
   * event record, so Stripe's retry processes it again.
   */
  @Transactional
  public void receive(byte[] body, String signature) {
    requireEnabled();
    String payload = new String(body, StandardCharsets.UTF_8);
    if (signature == null || signature.isBlank()) throw invalidEvent();
    try {
      Webhook.Signature.verifyHeader(
          payload, signature, webhookSecret, SIGNATURE_TOLERANCE_SECONDS, clock);
    } catch (SignatureVerificationException failure) {
      throw invalidEvent();
    }
    JsonNode event;
    try {
      event = JSON.readTree(payload);
    } catch (JacksonException failure) {
      throw invalidEvent();
    }
    String id = text(event.get("id"));
    String type = text(event.get("type"));
    if (id == null || type == null) throw invalidEvent();
    if (!HANDLED.contains(type)) return;
    String customer = text(event.path("data").path("object").get("customer"));
    if (!accounts.recordEvent(id, type, clock.instant()) || customer == null) return;
    var owner = accounts.ownerOfCustomer(customer);
    if (owner.isEmpty()) {
      // A customer created outside this platform (or in another environment's account).
      LOG.info("Stripe event for an unknown customer ignored: {}", type);
      return;
    }
    accounts.lockOwner(owner.get());
    reconcile(owner.get(), customer);
  }

  /**
   * Brings the owner's account in line with the subscription the Stripe API reports now; used by
   * the webhook and right after an upgrade the API has confirmed (ADR-0017 B13).
   */
  // Public on purpose: a non-public method called through the transactional proxy would run on the
  // proxy instance, whose fields are empty.
  public void reconcile(UUID owner, String customer) {
    var current = stripe.currentSubscription(customer);
    if (current.isEmpty()) return;
    var subscription = current.get();
    var choice = BillingCycle.parse(subscription.lookupKey());
    if (choice.isEmpty()) {
      LOG.warn("Stripe subscription with a price outside the plan catalog left unchanged");
      return;
    }
    BillingStatus status =
        switch (subscription.status()) {
          case "active", "trialing" -> BillingStatus.ACTIVE;
          case "past_due" -> BillingStatus.PAST_DUE;
          case "unpaid", "paused" -> BillingStatus.SUSPENDED;
          case "canceled" -> BillingStatus.CANCELED;
          default -> null;
        };
    if (status == null) return;
    Instant now = clock.instant();
    var account = accounts.find(owner).orElseThrow();
    // The grace counts from the first failed charge, not from each retry's event.
    Instant pastDueSince =
        status != BillingStatus.PAST_DUE
            ? null
            : "PAST_DUE".equals(account.status()) && account.pastDueSince() != null
                ? account.pastDueSince()
                : now;
    accounts.applySubscription(
        owner,
        status.name(),
        choice.get().plan(),
        choice.get().cycle().name(),
        subscription.currentPeriodEnd(),
        pastDueSince,
        subscription.id(),
        now);
  }

  private void requireEnabled() {
    if (!billing.enabled())
      throw new BillingException(BillingException.Reason.DISABLED, "Não encontrado.");
  }

  private static BillingException invalidEvent() {
    return new BillingException(BillingException.Reason.INVALID_EVENT, "Evento inválido.");
  }

  private static String text(JsonNode node) {
    return node != null && node.isString() && !node.asString().isBlank() ? node.asString() : null;
  }
}
