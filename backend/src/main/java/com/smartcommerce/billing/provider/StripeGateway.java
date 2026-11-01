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

  /** A customer portal session (change the card, see the receipts); returns its URL. */
  String portalUrl(String customer, String returnUrl);

  /**
   * The customer's subscription that decides his standing: the newest one that ever started (not
   * {@code incomplete} or {@code incomplete_expired}), or empty when there is none.
   */
  Optional<Subscription> currentSubscription(String customer);

  /** The price change a schedule holds for a later phase, or empty when it holds none. */
  Optional<PendingChange> pendingChange(String schedule);

  /**
   * What an immediate change to {@code price} charges now (ADR-0017 B13): the prorated invoice
   * Stripe would issue at {@code prorationDate}, in the currency's minor unit.
   */
  Preview previewChange(
      String customer, Subscription subscription, String price, long prorationDate);

  /**
   * Switches the subscription to {@code price} now and charges the prorated difference at once.
   * Returns false when the charge was declined or awaits authentication: Stripe keeps the old price
   * (pending update) and the open invoice is voided, so the upgrade can never apply later.
   */
  boolean changeNow(Subscription subscription, String price, long prorationDate);

  /**
   * Keeps the current price until the period ends and then moves to {@code price} for cycles of
   * {@code months} months, through a subscription schedule released afterwards.
   */
  void scheduleChange(Subscription subscription, String price, int months);

  /** Drops a schedule and its pending change; the subscription keeps its current price. */
  void releaseSchedule(String schedule);

  /** Marks (or unmarks) the subscription to end when the paid period ends. */
  void setCancelAtPeriodEnd(String subscription, boolean cancel);

  /** What the platform keeps of a Stripe subscription. */
  record Subscription(
      String id,
      String status,
      String lookupKey,
      Instant currentPeriodEnd,
      boolean cancelAtEnd,
      String itemId,
      String schedule) {}

  /** A price that takes over at {@code startsAt}. */
  record PendingChange(String lookupKey, Instant startsAt) {}

  /** An amount in the currency's minor unit (centavos for BRL) and its ISO code. */
  record Preview(long amountDue, String currency) {}
}
