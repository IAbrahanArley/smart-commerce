package com.smartcommerce.billing.services;

import com.smartcommerce.billing.domain.BillingAccount;
import com.smartcommerce.billing.domain.BillingException;
import com.smartcommerce.billing.domain.BillingStatus;
import com.smartcommerce.billing.domain.Plan;
import com.smartcommerce.billing.dto.response.BillingAccountView;
import com.smartcommerce.billing.repositories.BillingRepository;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan limits and account standing (SC-031, ADR-0017 B1, B2, B7, B9). While {@code billing.enabled}
 * is false (until the Stripe integration of SC-032 lets owners pay), nothing is limited and every
 * store stays as before; the account endpoint still reports the trial.
 */
@Service
public class BillingService {
  private static final Locale PT_BR = Locale.of("pt", "BR");

  private final BillingRepository accounts;
  private final Clock clock;
  private final boolean enabled;
  private final Duration trial;
  private final Duration grace;

  public BillingService(
      BillingRepository accounts,
      Clock clock,
      @Value("${billing.enabled:false}") boolean enabled,
      @Value("${billing.trial-days:14}") int trialDays,
      @Value("${billing.grace-days:7}") int graceDays) {
    if (trialDays < 1 || graceDays < 1)
      throw new IllegalStateException("billing.trial-days and billing.grace-days must be >= 1");
    this.accounts = accounts;
    this.clock = clock;
    this.enabled = enabled;
    this.trial = Duration.ofDays(trialDays);
    this.grace = Duration.ofDays(graceDays);
  }

  public boolean enabled() {
    return enabled;
  }

  /** The owner's account, opening the trial on first use. */
  @Transactional
  public BillingAccountView account(UUID user) {
    var account = load(user);
    var limits = account.limits();
    return new BillingAccountView(
        account.effective(clock.instant(), grace).name(),
        account.plan() == null ? null : account.plan().name(),
        account.cycle(),
        account.trialEndsAt(),
        account.currentPeriodEnd(),
        enabled,
        new BillingAccountView.Limits(
            limits.companies(),
            limits.productsPerStore(),
            limits.monthlyQuestions(),
            limits.staff()),
        accounts.companiesOwned(user));
  }

  /** Before creating a company: the owner is in good standing and has a free slot. */
  @Transactional
  public void requireCompanySlot(UUID owner) {
    if (!enabled) return;
    accounts.lockOwner(owner);
    var account = requireGoodStanding(owner);
    int limit = account.limits().companies();
    if (accounts.companiesOwned(owner) >= limit)
      throw new BillingException(
          BillingException.Reason.PLAN_LIMIT,
          "Seu plano permite até "
              + limit
              + (limit == 1 ? " loja" : " lojas")
              + ". Mude de plano para criar outra.");
  }

  /**
   * Before inviting an employee (ADR-0019 C1): the owner is in good standing and the people already
   * working for him, plus open invitations, leave a free slot. Counted once per person over all of
   * the owner's stores; {@code staffAndInvitations} is read only after the owner lock is held, so
   * two invitations at once cannot both take the last slot.
   */
  @Transactional
  public void requireStaffSlot(UUID owner, LongSupplier staffAndInvitations) {
    if (!enabled) return;
    accounts.lockOwner(owner);
    var account = requireGoodStanding(owner);
    int limit = account.limits().staff();
    if (staffAndInvitations.getAsLong() >= limit)
      throw new BillingException(
          BillingException.Reason.PLAN_LIMIT,
          "Seu plano permite até "
              + limit
              + (limit == 1 ? " funcionário" : " funcionários")
              + ", contando os convites em aberto. Mude de plano para convidar mais.");
  }

  /** Before creating a product in a store; a store without owner is left to the access check. */
  @Transactional
  public void requireProductSlot(UUID tenant) {
    if (!enabled) return;
    var owner = accounts.ownerOfTenant(tenant);
    if (owner.isEmpty()) return;
    var account = requireGoodStanding(owner.get());
    accounts.lockProducts(tenant);
    int limit = account.limits().productsPerStore();
    if (accounts.products(tenant) >= limit)
      throw new BillingException(
          BillingException.Reason.PLAN_LIMIT,
          "Seu plano permite até "
              + NumberFormat.getIntegerInstance(PT_BR).format(limit)
              + " produtos por loja. Mude de plano para cadastrar mais.");
  }

  /** Changes to a store (except its orders, which keep serving buyers) need good standing. */
  @Transactional
  public void requireWritable(UUID tenant) {
    if (!enabled) return;
    accounts.ownerOfTenant(tenant).ifPresent(this::requireGoodStanding);
  }

  /** Whether the public storefront of {@code slug} is served; unknown stores are left to it. */
  @Transactional(readOnly = true)
  public boolean storeOpen(String slug) {
    if (!enabled) return true;
    var owner = accounts.ownerOfStore(slug);
    if (owner.isEmpty()) return true;
    // An owner without an account has not used the panel since billing started: a fresh trial.
    return accounts
        .find(owner.get())
        .map(account -> account.effective(clock.instant(), grace).inGoodStanding())
        .orElse(true);
  }

  /**
   * Monthly assistant questions of the store's owner, counted over all of his stores. Empty while
   * billing is off: the assistant keeps its configured per-store quota (ADR-0016 A8).
   */
  @Transactional(readOnly = true)
  public Optional<OwnerQuota> questionQuota(UUID tenant) {
    if (!enabled) return Optional.empty();
    // Read-only: callers may be in a read-only transaction. No account yet means a fresh trial.
    return accounts
        .ownerOfTenant(tenant)
        .map(
            owner ->
                accounts
                    .find(owner)
                    .map(
                        account ->
                            new OwnerQuota(
                                owner,
                                account.effective(clock.instant(), grace).inGoodStanding()
                                    ? account.limits().monthlyQuestions()
                                    : 0))
                    .orElse(new OwnerQuota(owner, Plan.PROFISSIONAL.monthlyQuestions())));
  }

  public record OwnerQuota(UUID owner, int monthlyQuestions) {}

  private BillingAccount requireGoodStanding(UUID owner) {
    var account = load(owner);
    BillingStatus status = account.effective(clock.instant(), grace);
    if (!status.inGoodStanding())
      throw new BillingException(
          BillingException.Reason.PLAN_INACTIVE,
          status == BillingStatus.TRIAL_EXPIRED
              ? "Seu teste grátis terminou. Assine um plano para continuar fazendo alterações."
              : "Sua assinatura não está ativa. Regularize o plano para continuar fazendo"
                  + " alterações.");
    return account;
  }

  private BillingAccount load(UUID user) {
    Instant now = clock.instant();
    accounts.ensure(user, now, now.plus(trial));
    return accounts.find(user).orElseThrow();
  }
}
