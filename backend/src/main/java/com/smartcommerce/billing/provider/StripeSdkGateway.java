package com.smartcommerce.billing.provider;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.InvoiceCreatePreviewParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionScheduleCreateParams;
import com.stripe.param.SubscriptionScheduleRetrieveParams;
import com.stripe.param.SubscriptionScheduleUpdateParams;
import com.stripe.param.SubscriptionUpdateParams;
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
  // Schedule statuses that can still hold a change ahead (released or canceled ones cannot).
  private static final Set<String> SCHEDULED = Set.of("not_started", "active");
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

  @Override
  public Optional<PendingChange> pendingChange(String schedule) {
    return call(
        () -> {
          var found =
              stripe
                  .v1()
                  .subscriptionSchedules()
                  .retrieve(
                      schedule,
                      SubscriptionScheduleRetrieveParams.builder()
                          .addExpand("phases.items.price")
                          .build());
          if (!SCHEDULED.contains(found.getStatus()) || found.getCurrentPhase() == null)
            return Optional.empty();
          Long currentEnd = found.getCurrentPhase().getEndDate();
          return found.getPhases().stream()
              .filter(
                  phase -> phase.getStartDate() != null && phase.getStartDate().equals(currentEnd))
              .findFirst()
              .filter(phase -> !phase.getItems().isEmpty())
              .map(
                  phase ->
                      new PendingChange(
                          phase.getItems().getFirst().getPriceObject().getLookupKey(),
                          Instant.ofEpochSecond(phase.getStartDate())));
        });
  }

  @Override
  public Preview previewChange(
      String customer, Subscription subscription, String price, long prorationDate) {
    var params =
        InvoiceCreatePreviewParams.builder()
            .setCustomer(customer)
            .setSubscription(subscription.id())
            .setSubscriptionDetails(
                InvoiceCreatePreviewParams.SubscriptionDetails.builder()
                    .addItem(
                        InvoiceCreatePreviewParams.SubscriptionDetails.Item.builder()
                            .setId(subscription.itemId())
                            .setPrice(price)
                            .build())
                    .setProrationBehavior(
                        InvoiceCreatePreviewParams.SubscriptionDetails.ProrationBehavior
                            .ALWAYS_INVOICE)
                    .setProrationDate(prorationDate)
                    .build())
            .build();
    return call(
        () -> {
          var invoice = stripe.v1().invoices().createPreview(params);
          return new Preview(invoice.getAmountDue(), invoice.getCurrency());
        });
  }

  @Override
  public boolean changeNow(Subscription subscription, String price, long prorationDate) {
    var params =
        SubscriptionUpdateParams.builder()
            .addItem(
                SubscriptionUpdateParams.Item.builder()
                    .setId(subscription.itemId())
                    .setPrice(price)
                    .build())
            .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
            .setProrationDate(prorationDate)
            // A declined charge leaves the old price in place instead of an unpaid upgrade.
            .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.PENDING_IF_INCOMPLETE)
            .build();
    return call(
        () -> {
          com.stripe.model.Subscription updated;
          try {
            updated = stripe.v1().subscriptions().update(subscription.id(), params);
          } catch (CardException declined) {
            return false;
          }
          if (updated.getPendingUpdate() == null) return true;
          // Declined or waiting for authentication: void the open invoice, so it can never be paid
          // later and apply the upgrade after the owner was told it failed (or charge twice when
          // he tries again). The pending update then expires on its own.
          if (updated.getLatestInvoice() != null)
            stripe.v1().invoices().voidInvoice(updated.getLatestInvoice());
          return false;
        });
  }

  @Override
  public void scheduleChange(Subscription subscription, String price, int months) {
    call(
        () -> {
          var schedule =
              stripe
                  .v1()
                  .subscriptionSchedules()
                  .create(
                      SubscriptionScheduleCreateParams.builder()
                          .setFromSubscription(subscription.id())
                          .build());
          var current = schedule.getPhases().getFirst();
          var params =
              SubscriptionScheduleUpdateParams.builder()
                  .addPhase(
                      SubscriptionScheduleUpdateParams.Phase.builder()
                          .addItem(
                              SubscriptionScheduleUpdateParams.Phase.Item.builder()
                                  .setPrice(current.getItems().getFirst().getPrice())
                                  .setQuantity(1L)
                                  .build())
                          .setStartDate(current.getStartDate())
                          .setEndDate(current.getEndDate())
                          .build())
                  .addPhase(
                      SubscriptionScheduleUpdateParams.Phase.builder()
                          .addItem(
                              SubscriptionScheduleUpdateParams.Phase.Item.builder()
                                  .setPrice(price)
                                  .setQuantity(1L)
                                  .build())
                          .setDuration(
                              SubscriptionScheduleUpdateParams.Phase.Duration.builder()
                                  .setInterval(
                                      SubscriptionScheduleUpdateParams.Phase.Duration.Interval
                                          .MONTH)
                                  .setIntervalCount((long) months)
                                  .build())
                          .setProrationBehavior(
                              SubscriptionScheduleUpdateParams.Phase.ProrationBehavior.NONE)
                          .build())
                  // After the new phase the subscription runs on its own again, renewing as usual.
                  .setEndBehavior(SubscriptionScheduleUpdateParams.EndBehavior.RELEASE)
                  .build();
          try {
            return stripe.v1().subscriptionSchedules().update(schedule.getId(), params);
          } catch (StripeException failure) {
            // Never leave a half-built schedule holding the subscription.
            stripe.v1().subscriptionSchedules().release(schedule.getId());
            throw failure;
          }
        });
  }

  @Override
  public void releaseSchedule(String schedule) {
    call(() -> stripe.v1().subscriptionSchedules().release(schedule));
  }

  @Override
  public void setCancelAtPeriodEnd(String subscription, boolean cancel) {
    call(
        () ->
            stripe
                .v1()
                .subscriptions()
                .update(
                    subscription,
                    SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(cancel).build()));
  }

  private static StripeGateway.Subscription snapshot(com.stripe.model.Subscription subscription) {
    var item = subscription.getItems().getData().getFirst();
    Long end = item.getCurrentPeriodEnd();
    return new StripeGateway.Subscription(
        subscription.getId(),
        subscription.getStatus(),
        item.getPrice().getLookupKey(),
        end == null ? null : Instant.ofEpochSecond(end),
        Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
        item.getId(),
        subscription.getSchedule());
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
