package com.smartcommerce.orders.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CheckoutSafetyCheckTest {
  @Test
  void enabledCheckoutOutsideDevRequiresTheHostedTestEnvironmentAndTheSignedIpSecret() {
    // D6: outside dev, checkout without payments exists only in the hosted test environment.
    assertThatThrownBy(() -> CheckoutSafetyCheck.verify(true, true, false, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hosted-test");
    assertThatThrownBy(() -> CheckoutSafetyCheck.verify(true, false, false, true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("STOREFRONT_FORWARDING_SECRET");
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, true, false, true))
        .doesNotThrowAnyException();
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, false, true, false))
        .doesNotThrowAnyException();
    assertThatCode(() -> CheckoutSafetyCheck.verify(false, false, false, false))
        .doesNotThrowAnyException();
  }
}
