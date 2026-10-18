package com.smartcommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.tenant.domain.FulfillmentSettings;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FulfillmentSettingsTest {
  @Test
  void zeroMeansFreeShippingAndAmountsKeepExactScale() {
    var free = FulfillmentSettings.parse(false, null, true, "0", "Centro");
    assertThat(free.fixedShippingAmount()).isEqualTo(new BigDecimal("0.00"));
    var paid = FulfillmentSettings.parse(false, null, true, "12.5", "Centro");
    assertThat(paid.fixedShippingAmount()).isEqualTo(new BigDecimal("12.50"));
    var max = FulfillmentSettings.parse(false, null, true, "99999999999999999.99", "Centro");
    assertThat(max.fixedShippingAmount().toPlainString()).isEqualTo("99999999999999999.99");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"-1", "-0.01", "1.234", "0.001", "1,50", "1e3", " 1", "", "100000000000000000"})
  void rejectsNegativeOverScaledAndNonCanonicalAmounts(String amount) {
    assertThatThrownBy(() -> FulfillmentSettings.parse(false, null, true, amount, "Centro"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Rua A\nnúmero 1", "Rua\tA", "Rua\u0000A", "Rua\u200bA", "Rua\u2028A"})
  void rejectsControlAndFormatCharacters(String text) {
    assertThatThrownBy(() -> FulfillmentSettings.parse(true, text, false, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FulfillmentSettings.parse(false, null, true, "1", text))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void limitsTextsTo500CodePointsAfterTrimming() {
    String exact = "á".repeat(500);
    assertThat(FulfillmentSettings.parse(true, "  " + exact + "  ", false, null, null))
        .extracting(FulfillmentSettings::pickupInstructions)
        .isEqualTo(exact);
    assertThatThrownBy(() -> FulfillmentSettings.parse(true, exact + "a", false, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FulfillmentSettings.parse(false, null, true, "1", "😀".repeat(501)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void enabledOptionsRequireTheirTextsWhileDisabledOptionsKeepThem() {
    assertThatThrownBy(() -> FulfillmentSettings.parse(true, "   ", false, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FulfillmentSettings.parse(false, null, true, null, "Centro"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FulfillmentSettings.parse(false, null, true, "5.00", null))
        .isInstanceOf(IllegalArgumentException.class);
    var disabled = FulfillmentSettings.parse(false, "Loja física", false, "5", "Centro");
    assertThat(disabled.pickupInstructions()).isEqualTo("Loja física");
    assertThat(disabled.fixedShippingAmount()).isEqualTo(new BigDecimal("5.00"));
    assertThat(FulfillmentSettings.parse(false, null, false, null, null))
        .isEqualTo(FulfillmentSettings.disabled());
  }
}
