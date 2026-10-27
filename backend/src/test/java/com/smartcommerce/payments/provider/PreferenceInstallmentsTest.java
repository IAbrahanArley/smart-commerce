package com.smartcommerce.payments.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-042 (ADR-0018 V4): the installment limit is sent only when the store set one. */
class PreferenceInstallmentsTest {
  private final MercadoPagoCheckoutClient client =
      new MercadoPagoCheckoutClient("http://127.0.0.1:1", "https://loja.example.test", "");
  private final CheckoutPaymentRequest request =
      new CheckoutPaymentRequest(
          UUID.randomUUID(),
          "loja-teste",
          UUID.randomUUID(),
          new BigDecimal("49.90"),
          List.of(new CheckoutPaymentRequest.Line("Camiseta", new BigDecimal("49.90"), 1)));

  @SuppressWarnings("unchecked")
  private Map<String, Object> methods(Integer installments) {
    return (Map<String, Object>)
        client.body(request, Instant.now(), installments).get("payment_methods");
  }

  @Test
  void sendsTheLimitOnlyWhenSet() {
    assertThat(methods(null)).containsOnlyKeys("excluded_payment_types");
    assertThat(methods(6)).containsEntry("installments", 6);
    assertThat(client.body(request, Instant.now()).get("payment_methods")).isEqualTo(methods(null));
    for (int invalid : new int[] {0, 13})
      assertThatThrownBy(() -> methods(invalid)).isInstanceOf(IllegalArgumentException.class);
  }
}
