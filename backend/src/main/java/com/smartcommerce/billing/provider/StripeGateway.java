package com.smartcommerce.billing.provider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The few Stripe operations the platform subscription needs (ADR-0017). The service talks to this
 * interface only; {@link StripeSdkGateway} is the one implementation and tests point it at a fake
 * server. Card data never passes here: the card is typed on Stripe's hosted Checkout (B3).
 */
public interface StripeGateway {
  /** A Stripe customer for the owner, tagged with the owner's id. */
  String createCustomer(UUID owner, String email);

  /** The active recurring price with this lookup key, if Stripe has one. */
  Optional<String> priceId(String lookupKey);

  /** A hosted Checkout page in subscription mode for one price; returns its URL. */
  String checkoutUrl(
      String customer, String price, UUID owner, String successUrl, String cancelUrl);

  /** A customer portal session (change card, plan or cycle, cancel); returns its URL. */
  String portalUrl(String customer, String returnUrl);

  /**
   * The customer's subscription that decides his standing: the newest one that ever started (not
   * {@code incomplete} or {@code incomplete_expired}), or empty when there is none.
   */
  Optional<Subscription> currentSubscription(String customer);

  /** What the platform keeps of a Stripe subscription. */
  record Subscription(
      String id, String status, String lookupKey, Instant currentPeriodEnd, boolean cancelAtEnd) {}
}
