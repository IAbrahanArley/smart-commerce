package com.smartcommerce.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.billing.domain.BillingAccount;
import com.smartcommerce.billing.domain.BillingStatus;
import com.smartcommerce.billing.domain.Plan;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0017 B2, B7: the trial and the grace period end by time alone, and so do the limits. */
class BillingAccountTest {
  private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
  private static final Duration GRACE = Duration.ofDays(7);

  @Test
  void trialEndsAtItsDateAndUnlocksProfissional() {
    var trial = account("TRIAL", null, NOW.plusSeconds(1), null);
    assertThat(trial.effective(NOW, GRACE)).isEqualTo(BillingStatus.TRIAL);
    assertThat(trial.limits()).isEqualTo(Plan.PROFISSIONAL);
    var ended = account("TRIAL", null, NOW, null);
    assertThat(ended.effective(NOW, GRACE)).isEqualTo(BillingStatus.TRIAL_EXPIRED);
    assertThat(ended.effective(NOW, GRACE).inGoodStanding()).isFalse();
  }

  @Test
  void failedChargeKeepsGoodStandingDuringTheGraceOnly() {
    var recent = account("PAST_DUE", Plan.ESCALA, NOW, NOW.minus(Duration.ofDays(6)));
    assertThat(recent.effective(NOW, GRACE)).isEqualTo(BillingStatus.PAST_DUE);
    assertThat(recent.effective(NOW, GRACE).inGoodStanding()).isTrue();
    assertThat(recent.limits()).isEqualTo(Plan.ESCALA);
    var old = account("PAST_DUE", Plan.ESCALA, NOW, NOW.minus(GRACE));
    assertThat(old.effective(NOW, GRACE)).isEqualTo(BillingStatus.SUSPENDED);
    assertThat(
            account("CANCELED", Plan.ESSENCIAL, NOW, null).effective(NOW, GRACE).inGoodStanding())
        .isFalse();
    assertThat(account("ACTIVE", Plan.ESSENCIAL, NOW, null).effective(NOW, GRACE).inGoodStanding())
        .isTrue();
  }

  @Test
  void plansMatchTheApprovedTable() {
    assertThat(Plan.ESSENCIAL.companies()).isEqualTo(1);
    assertThat(Plan.PROFISSIONAL.companies()).isEqualTo(2);
    assertThat(Plan.ESCALA.companies()).isEqualTo(5);
    assertThat(Plan.ESSENCIAL.productsPerStore()).isEqualTo(150);
    assertThat(Plan.PROFISSIONAL.productsPerStore()).isEqualTo(2_000);
    assertThat(Plan.ESSENCIAL.monthlyQuestions()).isZero();
    assertThat(Plan.PROFISSIONAL.monthlyQuestions()).isEqualTo(60);
    assertThat(Plan.ESCALA.monthlyQuestions()).isEqualTo(200);
  }

  private static BillingAccount account(
      String status, Plan plan, Instant trialEnd, Instant pastDue) {
    return new BillingAccount(
        UUID.randomUUID(), status, plan, plan == null ? null : "MONTHLY", trialEnd, null, pastDue);
  }
}
