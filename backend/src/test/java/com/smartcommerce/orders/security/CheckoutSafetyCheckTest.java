package com.smartcommerce.orders.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CheckoutSafetyCheckTest {
  @Test
  void enabledCheckoutOutsideDevRequiresTheHostedTestEnvironmentAndTheSignedIpSecret() {
    // D6: outside dev, checkout without production payments exists only in the hosted test
    // environment.
    assertThatThrownBy(() -> CheckoutSafetyCheck.verify(true, true, false, false, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hosted-test")
        .hasMessageContaining("MERCADO_PAGO_TEST_TOKEN=false");
    assertThatThrownBy(() -> CheckoutSafetyCheck.verify(true, false, false, true, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("STOREFRONT_FORWARDING_SECRET");
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, true, false, true, false))
        .doesNotThrowAnyException();
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, false, true, false, false))
        .doesNotThrowAnyException();
    assertThatCode(() -> CheckoutSafetyCheck.verify(false, false, false, false, false))
        .doesNotThrowAnyException();
  }

  @Test
  void productionCheckoutRequiresRealPaymentsAndTheSignedIpSecret() {
    // MVP block 2b: production runs checkout only with the integration, webhook and production
    // tokens; the signed-IP secret is still mandatory.
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, true, false, false, true))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> CheckoutSafetyCheck.verify(true, false, false, false, true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("STOREFRONT_FORWARDING_SECRET");
  }
}
