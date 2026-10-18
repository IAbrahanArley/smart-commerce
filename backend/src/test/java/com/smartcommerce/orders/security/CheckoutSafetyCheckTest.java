package com.smartcommerce.orders.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CheckoutSafetyCheckTest {
  @Test
  void enabledCheckoutOutsideDevRequiresTheSignedIpSecret() {
    assertThatThrownBy(() -> CheckoutSafetyCheck.verify(true, false, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("STOREFRONT_FORWARDING_SECRET");
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, true, false)).doesNotThrowAnyException();
    assertThatCode(() -> CheckoutSafetyCheck.verify(true, false, true)).doesNotThrowAnyException();
    assertThatCode(() -> CheckoutSafetyCheck.verify(false, false, false))
        .doesNotThrowAnyException();
  }
}
