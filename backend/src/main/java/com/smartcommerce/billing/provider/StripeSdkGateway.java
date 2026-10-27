package com.smartcommerce.billing.provider;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.billingportal.SessionCreateParams;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link StripeGateway} over the official SDK (ADR-0017 B11). Failures surface as {@link
 * StripeUnavailableException}, never with the provider's message (which may echo request data).
 */
public final class StripeSdkGateway implements StripeGateway {
  private static final Set<String> NEVER_STARTED = Set.of("incomplete", "incomplete_expired");
  private final StripeClient stripe;

  /** {@code stripe} is null when no key is configured: every call then fails as unavailable. */
  public StripeSdkGateway(StripeClient stripe) {
    this.stripe = stripe;
  }

  @Override
  public String createCustomer(UUID owner, String email) {
    return call(
        () ->
            stripe
                .v1()
                .customers()
                .create(
                    CustomerCreateParams.builder()
                        .setEmail(email)
                        .putMetadata("owner_id", owner.toString())
                        .build())
                .getId());
  }

  @Override
  public Optional<String> priceId(String lookupKey) {
    return call(
        () ->
            stripe
                .v1()
                .prices()
                .list(
                    PriceListParams.builder()
                        .addLookupKey(lookupKey)
                        .setActive(true)
                        .setLimit(1L)
                        .build())
                .getData()
                .stream()
                .findFirst()
                .map(com.stripe.model.Price::getId));
  }

  @Override
  public String checkoutUrl(
      String customer, String price, UUID owner, String successUrl, String cancelUrl) {
    var params =
        com.stripe.param.checkout.SessionCreateParams.builder()
            .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customer)
            .setClientReferenceId(owner.toString())
            .addLineItem(
                com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                    .setPrice(price)
                    .setQuantity(1L)
                    .build())
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setLocale(com.stripe.param.checkout.SessionCreateParams.Locale.PT_BR)
            .putMetadata("owner_id", owner.toString())
            .setSubscriptionData(
                com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                    .putMetadata("owner_id", owner.toString())
                    .build())
            .build();
    return call(() -> stripe.v1().checkout().sessions().create(params).getUrl());
  }

  @Override
  public String portalUrl(String customer, String returnUrl) {
    return call(
        () ->
            stripe
                .v1()
                .billingPortal()
                .sessions()
                .create(
                    SessionCreateParams.builder()
                        .setCustomer(customer)
                        .setReturnUrl(returnUrl)
                        .build())
                .getUrl());
  }

  @Override
  public Optional<StripeGateway.Subscription> currentSubscription(String customer) {
    return call(
        () ->
            stripe
                .v1()
                .subscriptions()
                .list(
                    SubscriptionListParams.builder()
                        .setCustomer(customer)
                        .setStatus(SubscriptionListParams.Status.ALL)
                        .setLimit(10L)
                        .build())
                .getData()
                .stream()
                .filter(subscription -> !NEVER_STARTED.contains(subscription.getStatus()))
                .max(Comparator.comparing(com.stripe.model.Subscription::getCreated))
                .map(StripeSdkGateway::snapshot));
  }

  private static StripeGateway.Subscription snapshot(com.stripe.model.Subscription subscription) {
    var item = subscription.getItems().getData().getFirst();
    Long end = item.getCurrentPeriodEnd();
    return new StripeGateway.Subscription(
        subscription.getId(),
        subscription.getStatus(),
        item.getPrice().getLookupKey(),
        end == null ? null : Instant.ofEpochSecond(end),
        Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
  }

  private interface Call<T> {
    T run() throws StripeException;
  }

  private <T> T call(Call<T> call) {
    if (stripe == null) throw new StripeUnavailableException(null);
    try {
      return call.run();
    } catch (StripeException failure) {
      throw new StripeUnavailableException(failure);
    }
  }
}
