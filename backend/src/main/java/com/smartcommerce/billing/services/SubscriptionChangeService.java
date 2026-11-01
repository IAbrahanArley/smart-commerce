package com.smartcommerce.billing.services;

import com.smartcommerce.billing.domain.BillingCycle;
import com.smartcommerce.billing.domain.BillingException;
import com.smartcommerce.billing.domain.Plan;
import com.smartcommerce.billing.domain.PlanChange;
import com.smartcommerce.billing.dto.response.ChangePreview;
import com.smartcommerce.billing.dto.response.SubscriptionView;
import com.smartcommerce.billing.provider.StripeGateway;
import com.smartcommerce.billing.provider.StripeGateway.Subscription;
import com.smartcommerce.billing.provider.StripeUnavailableException;
import com.smartcommerce.billing.repositories.BillingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan and cycle changes, cancellation and resumption inside the panel (ADR-0017 B13), replacing
 * the customer portal for those (the portal keeps the card and the receipts). Upgrades apply at
 * once and charge the prorated difference, which the owner sees in a preview first; downgrades are
 * scheduled for the end of the paid period; cancellation always waits for the end of the period.
 * Every decision reads the subscription from Stripe, and the account itself still follows the
 * subscription the API reports, never the browser.
 */
@Service
public class SubscriptionChangeService {
  /** How long a preview's amount may be confirmed; older confirmations must preview again. */
  static final long PREVIEW_VALIDITY_SECONDS = 600;

  private static final Set<String> CHANGEABLE = Set.of("active", "trialing");

  private final BillingService billing;
  private final BillingRepository accounts;
  private final StripeGateway stripe;
  private final StripeBillingService paid;
  private final Clock clock;

  public SubscriptionChangeService(
      BillingService billing,
      BillingRepository accounts,
      StripeGateway stripe,
      StripeBillingService paid,
      Clock clock) {
    this.billing = billing;
    this.accounts = accounts;
    this.stripe = stripe;
    this.paid = paid;
    this.clock = clock;
  }

  /** The running subscription as Stripe reports it, with any change scheduled ahead. */
  @Transactional(readOnly = true)
  public SubscriptionView subscription(UUID owner) {
    requireEnabled();
    return running(owner).map(this::view).orElse(SubscriptionView.none());
  }

  /** What changing to {@code plan} and {@code cycle} would do, without doing it. */
  @Transactional(readOnly = true)
  public ChangePreview preview(UUID owner, Plan plan, BillingCycle cycle) {
    var target = target(owner, plan, cycle);
    boolean exceeds = accounts.companiesOwned(owner) > plan.companies();
    if (target.timing() == PlanChange.AT_PERIOD_END)
      return new ChangePreview(
          target.timing().name(),
          plan.name(),
          cycle.name(),
          null,
          null,
          target.subscription().currentPeriodEnd(),
          null,
          exceeds);
    long now = clock.instant().getEpochSecond();
    var preview =
        stripe.previewChange(target.customer(), target.subscription(), target.price(), now);
    return new ChangePreview(
        target.timing().name(),
        plan.name(),
        cycle.name(),
        amount(preview),
        preview.currency().toUpperCase(Locale.ROOT),
        clock.instant(),
        now,
        exceeds);
  }

  /**
   * Applies the change. An upgrade needs the preview's {@code prorationDate}, at most {@link
   * #PREVIEW_VALIDITY_SECONDS} old, and the amount the owner saw: Stripe is asked again for that
   * same date, and any difference (another plan, a forged date) is refused before charging. A
   * declined card leaves the plan as it was.
   */
  @Transactional
  public SubscriptionView change(
      UUID owner, Plan plan, BillingCycle cycle, Long prorationDate, BigDecimal expectedAmount) {
    requireEnabled();
    accounts.lockOwner(owner);
    var target = target(owner, plan, cycle);
    var subscription = target.subscription();
    if (target.timing() == PlanChange.AT_PERIOD_END) {
      stripe.scheduleChange(subscription, target.price(), cycle.months());
      return current(target.customer());
    }
    long now = clock.instant().getEpochSecond();
    if (prorationDate == null
        || expectedAmount == null
        || prorationDate > now
        || prorationDate < now - PREVIEW_VALIDITY_SECONDS
        || amount(
                    stripe.previewChange(
                        target.customer(), subscription, target.price(), prorationDate))
                .compareTo(expectedAmount)
            != 0)
      throw new BillingException(
          BillingException.Reason.PREVIEW_EXPIRED,
          "O valor da mudança precisa ser conferido de novo. Revise e confirme outra vez.");
    if (!stripe.changeNow(subscription, target.price(), prorationDate))
      throw new BillingException(
          BillingException.Reason.PAYMENT_DECLINED,
          "O cartão recusou a cobrança da diferença e seu plano não mudou. Atualize o cartão em"
              + " \"Cartão e recibos\" e tente de novo.");
    try {
      paid.reconcile(owner, target.customer());
      return current(target.customer());
    } catch (StripeUnavailableException failure) {
      // Charged and applied: a failed read afterwards must not look like a failed change. The
      // webhook brings the account in line.
      return new SubscriptionView(
          subscription.status(),
          plan.name(),
          cycle.name(),
          subscription.currentPeriodEnd(),
          false,
          null);
    }
  }

  /** Drops the change scheduled for the end of the period; the current plan simply renews. */
  @Transactional
  public SubscriptionView undoPendingChange(UUID owner) {
    requireEnabled();
    accounts.lockOwner(owner);
    var subscription = running(owner).orElseThrow(SubscriptionChangeService::noSubscription);
    if (subscription.schedule() == null) throw notAllowed("Não há mudança de plano agendada.");
    stripe.releaseSchedule(subscription.schedule());
    return current(accounts.stripeCustomer(owner).orElseThrow());
  }

  /**
   * Ends the subscription when the paid period ends (never at once: an immediate cancellation with
   * refund is done by a person, ADR-0017 B10). A scheduled change must be undone first, so a failed
   * call can never drop it silently.
   */
  @Transactional
  public SubscriptionView cancel(UUID owner) {
    requireEnabled();
    accounts.lockOwner(owner);
    var subscription = running(owner).orElseThrow(SubscriptionChangeService::noSubscription);
    if (subscription.schedule() != null)
      throw notAllowed("Desfaça a mudança de plano agendada antes de cancelar.");
    if (!subscription.cancelAtEnd()) stripe.setCancelAtPeriodEnd(subscription.id(), true);
    return current(accounts.stripeCustomer(owner).orElseThrow());
  }

  /** Keeps a subscription that was marked to end; it renews as before. */
  @Transactional
  public SubscriptionView resume(UUID owner) {
    requireEnabled();
    accounts.lockOwner(owner);
    var subscription = running(owner).orElseThrow(SubscriptionChangeService::noSubscription);
    if (subscription.cancelAtEnd()) stripe.setCancelAtPeriodEnd(subscription.id(), false);
    return current(accounts.stripeCustomer(owner).orElseThrow());
  }

  private record Target(
      String customer, Subscription subscription, String price, PlanChange timing) {}

  private Target target(UUID owner, Plan plan, BillingCycle cycle) {
    requireEnabled();
    String customer =
        accounts.stripeCustomer(owner).orElseThrow(SubscriptionChangeService::noSubscription);
    var subscription = running(owner).orElseThrow(SubscriptionChangeService::noSubscription);
    if (!CHANGEABLE.contains(subscription.status()))
      throw notAllowed("Regularize o pagamento da assinatura antes de mudar de plano.");
    if (subscription.cancelAtEnd())
      throw notAllowed(
          "Sua assinatura está marcada para encerrar. Mantenha a assinatura antes de mudar de"
              + " plano.");
    // One change at a time: Stripe does not touch the items of a scheduled subscription, and
    // releasing the schedule first would lose it whenever the next call fails.
    if (subscription.schedule() != null)
      throw notAllowed(
          "Você já tem uma mudança de plano agendada. Desfaça essa mudança para escolher outra.");
    var current =
        BillingCycle.parse(subscription.lookupKey())
            .orElseThrow(() -> notAllowed("Este plano não pode ser alterado pelo painel."));
    if (current.plan() == plan && current.cycle() == cycle)
      throw notAllowed("Esse já é o seu plano atual.");
    String price =
        stripe
            .priceId(BillingCycle.lookupKey(plan, cycle))
            .orElseThrow(() -> new StripeUnavailableException(null));
    return new Target(
        customer, subscription, price, PlanChange.of(current.plan(), current.cycle(), plan, cycle));
  }

  private static BigDecimal amount(StripeGateway.Preview preview) {
    String currency = preview.currency().toUpperCase(Locale.ROOT);
    return BigDecimal.valueOf(
        preview.amountDue(), Currency.getInstance(currency).getDefaultFractionDigits());
  }

  private Optional<Subscription> running(UUID owner) {
    return accounts
        .stripeCustomer(owner)
        .flatMap(stripe::currentSubscription)
        .filter(subscription -> !"canceled".equals(subscription.status()));
  }

  private SubscriptionView current(String customer) {
    return stripe
        .currentSubscription(customer)
        .filter(subscription -> !"canceled".equals(subscription.status()))
        .map(this::view)
        .orElse(SubscriptionView.none());
  }

  private SubscriptionView view(Subscription subscription) {
    var choice = BillingCycle.parse(subscription.lookupKey());
    var pending =
        subscription.schedule() == null
            ? Optional.<SubscriptionView.Pending>empty()
            : stripe
                .pendingChange(subscription.schedule())
                .flatMap(
                    change ->
                        BillingCycle.parse(change.lookupKey())
                            .map(
                                next ->
                                    new SubscriptionView.Pending(
                                        next.plan().name(),
                                        next.cycle().name(),
                                        change.startsAt())));
    return new SubscriptionView(
        subscription.status(),
        choice.map(value -> value.plan().name()).orElse(null),
        choice.map(value -> value.cycle().name()).orElse(null),
        subscription.currentPeriodEnd(),
        subscription.cancelAtEnd(),
        pending.orElse(null));
  }

  private void requireEnabled() {
    if (!billing.enabled())
      throw new BillingException(BillingException.Reason.DISABLED, "Não encontrado.");
  }

  private static BillingException noSubscription() {
    return new BillingException(
        BillingException.Reason.NO_SUBSCRIPTION,
        "Você ainda não tem assinatura. Escolha um plano para assinar.");
  }

  private static BillingException notAllowed(String message) {
    return new BillingException(BillingException.Reason.CHANGE_NOT_ALLOWED, message);
  }
}
