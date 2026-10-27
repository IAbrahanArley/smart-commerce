package com.smartcommerce.advisor.services;

import com.smartcommerce.advisor.domain.AdvisorException;
import com.smartcommerce.advisor.domain.AdvisorUsage;
import com.smartcommerce.advisor.dto.response.AdvisorStatusView;
import com.smartcommerce.advisor.repositories.AdvisorUsageRepository;
import com.smartcommerce.billing.services.BillingService;
import com.smartcommerce.finance.domain.ReportPeriod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assistant switch and limits (SC-025, ADR-0016 A8, A11). A question starts only when the assistant
 * is on, the actor owns the store, the store has questions left this month (calendar month in São
 * Paulo) and the platform is under its monthly spending cap. Starting records a PENDING row under
 * advisory locks, so concurrent questions never overshoot either limit; the row is closed with what
 * the question consumed.
 */
@Service
public class AdvisorQuotaService {
  private final AdvisorUsageRepository usage;
  private final Clock clock;
  private final boolean enabled;
  private final int monthlyQuota;
  private final BigDecimal monthlyCapUsd;
  private final BillingService billing;

  public AdvisorQuotaService(
      AdvisorUsageRepository usage,
      Clock clock,
      BillingService billing,
      @Value("${advisor.enabled:false}") boolean enabled,
      @Value("${advisor.monthly-question-quota:100}") int monthlyQuota,
      @Value("${advisor.global-monthly-cap-usd:20.00}") BigDecimal monthlyCapUsd) {
    if (monthlyQuota < 1) throw new IllegalStateException("advisor.monthly-question-quota >= 1");
    if (monthlyCapUsd == null || monthlyCapUsd.signum() <= 0)
      throw new IllegalStateException("advisor.global-monthly-cap-usd must be positive");
    this.usage = usage;
    this.clock = clock;
    this.enabled = enabled;
    this.monthlyQuota = monthlyQuota;
    this.monthlyCapUsd = monthlyCapUsd;
    this.billing = billing;
  }

  public boolean enabled() {
    return enabled;
  }

  @Transactional(readOnly = true)
  public AdvisorStatusView status(UUID actor, UUID tenant) {
    requireOwner(actor, tenant);
    // SC-031: with billing on, the quota is the owner's plan, counted over all of his stores.
    var plan = billing.questionQuota(tenant);
    int quota = plan.map(BillingService.OwnerQuota::monthlyQuestions).orElse(monthlyQuota);
    long used =
        plan.map(owner -> usage.countedSinceForOwner(owner.owner(), monthStart()))
            .orElseGet(() -> usage.countedSince(tenant, monthStart()));
    return new AdvisorStatusView(enabled, quota, used, Math.max(0, quota - used));
  }

  /** Reserves one question of the store's quota; returns the usage row to close later. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public UUID start(UUID actor, UUID tenant) {
    if (!enabled) throw new AdvisorException(AdvisorException.Reason.DISABLED);
    requireOwner(actor, tenant);
    Instant since = monthStart();
    usage.lockGlobal();
    if (usage.costSince(since).compareTo(monthlyCapUsd) >= 0)
      throw new AdvisorException(AdvisorException.Reason.GLOBAL_CAP_REACHED);
    var plan = billing.questionQuota(tenant);
    if (plan.isPresent()) {
      usage.lockOwner(plan.get().owner());
      if (usage.countedSinceForOwner(plan.get().owner(), since) >= plan.get().monthlyQuestions())
        throw new AdvisorException(AdvisorException.Reason.QUOTA_EXCEEDED);
    } else {
      usage.lockStore(tenant);
      if (usage.countedSince(tenant, since) >= monthlyQuota)
        throw new AdvisorException(AdvisorException.Reason.QUOTA_EXCEEDED);
    }
    UUID id = UUID.randomUUID();
    usage.insertPending(id, tenant, actor, now());
    return id;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public boolean finish(UUID id, UUID tenant, AdvisorUsage consumed) {
    return usage.finish(id, tenant, consumed, now());
  }

  private void requireOwner(UUID actor, UUID tenant) {
    if (!usage.ownsStore(actor, tenant))
      throw new AdvisorException(AdvisorException.Reason.MISSING);
  }

  private Instant monthStart() {
    LocalDate today = LocalDate.now(clock.withZone(ReportPeriod.ZONE));
    return today.withDayOfMonth(1).atStartOfDay(ReportPeriod.ZONE).toInstant();
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
