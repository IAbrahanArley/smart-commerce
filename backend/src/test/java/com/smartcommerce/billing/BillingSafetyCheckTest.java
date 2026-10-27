package com.smartcommerce.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.billing.config.BillingSafetyCheck;
import com.smartcommerce.billing.domain.BillingCycle;
import com.smartcommerce.billing.domain.Plan;
import org.junit.jupiter.api.Test;

/** SC-032 startup guard and the price lookup keys (ADR-0017 B4, B12). */
class BillingSafetyCheckTest {
  @Test
  void billingOnRequiresBothStripeSecrets() {
    assertThatThrownBy(() -> BillingSafetyCheck.verify(true, "", "whsec_x", false))
        .hasMessageContaining("STRIPE_SECRET_KEY");
    assertThatThrownBy(() -> BillingSafetyCheck.verify(true, "sk_test_x", "", false))
        .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    assertThatCode(() -> BillingSafetyCheck.verify(false, "", "", true)).doesNotThrowAnyException();
    assertThatCode(() -> BillingSafetyCheck.verify(true, "rk_test_x", "whsec_x", false))
        .doesNotThrowAnyException();
  }

  @Test
  void keysNeverCrossEnvironments() {
    assertThatThrownBy(() -> BillingSafetyCheck.verify(true, "sk_test_x", "whsec_x", true))
        .hasMessageContaining("live");
    assertThatThrownBy(() -> BillingSafetyCheck.verify(false, "sk_live_x", "", false))
        .hasMessageContaining("test keys");
    assertThatThrownBy(() -> BillingSafetyCheck.verify(true, "pk_test_x", "whsec_x", false))
        .hasMessageContaining("test keys");
    assertThatCode(() -> BillingSafetyCheck.verify(true, "sk_live_x", "whsec_x", true))
        .doesNotThrowAnyException();
  }

  @Test
  void everyPlanAndCycleHasOneLookupKeyThatParsesBack() {
    assertThat(BillingCycle.lookupKey(Plan.PROFISSIONAL, BillingCycle.ANNUAL))
        .isEqualTo("profissional_anual");
    for (Plan plan : Plan.values())
      for (BillingCycle cycle : BillingCycle.values())
        assertThat(BillingCycle.parse(BillingCycle.lookupKey(plan, cycle)))
            .contains(new BillingCycle.Choice(plan, cycle));
    assertThat(BillingCycle.parse("premium_mensal")).isEmpty();
    assertThat(BillingCycle.parse(null)).isEmpty();
  }
}
