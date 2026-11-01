package com.smartcommerce.billing;

import static com.smartcommerce.billing.domain.BillingCycle.ANNUAL;
import static com.smartcommerce.billing.domain.BillingCycle.MONTHLY;
import static com.smartcommerce.billing.domain.BillingCycle.SEMIANNUAL;
import static com.smartcommerce.billing.domain.Plan.ESCALA;
import static com.smartcommerce.billing.domain.Plan.ESSENCIAL;
import static com.smartcommerce.billing.domain.Plan.PROFISSIONAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.billing.domain.PlanChange;
import org.junit.jupiter.api.Test;

/** ADR-0017 B13: upgrades apply now, everything else waits for the end of the paid period. */
class PlanChangeTest {
  @Test
  void aLargerPlanOrALongerCycleAppliesNow() {
    assertThat(PlanChange.of(ESSENCIAL, MONTHLY, PROFISSIONAL, MONTHLY)).isEqualTo(PlanChange.NOW);
    assertThat(PlanChange.of(ESSENCIAL, MONTHLY, ESSENCIAL, ANNUAL)).isEqualTo(PlanChange.NOW);
    assertThat(PlanChange.of(ESSENCIAL, SEMIANNUAL, ESCALA, ANNUAL)).isEqualTo(PlanChange.NOW);
  }

  @Test
  void aSmallerPlanAShorterCycleOrAMixWaitsForThePeriodEnd() {
    assertThat(PlanChange.of(ESCALA, MONTHLY, ESSENCIAL, MONTHLY))
        .isEqualTo(PlanChange.AT_PERIOD_END);
    assertThat(PlanChange.of(ESSENCIAL, ANNUAL, ESSENCIAL, MONTHLY))
        .isEqualTo(PlanChange.AT_PERIOD_END);
    // A larger plan on a shorter cycle is not an upgrade of both: it waits too.
    assertThat(PlanChange.of(ESSENCIAL, ANNUAL, ESCALA, MONTHLY))
        .isEqualTo(PlanChange.AT_PERIOD_END);
  }

  @Test
  void theSamePlanAndCycleIsNoChange() {
    assertThatThrownBy(() -> PlanChange.of(PROFISSIONAL, ANNUAL, PROFISSIONAL, ANNUAL))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
