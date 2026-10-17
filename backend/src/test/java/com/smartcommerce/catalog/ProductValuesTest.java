package com.smartcommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smartcommerce.catalog.domain.ProductValues;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductValuesTest {
  @Test
  void moneyIsExactAndMissingCostRemainsUnknown() {
    var value = ProductValues.parse(" Nome ", " aA/01 ", "12345678901234567.89", null);
    assertThat(value.name()).isEqualTo("Nome");
    assertThat(value.sku()).isEqualTo("aA/01");
    assertThat(value.price()).isEqualTo(new BigDecimal("12345678901234567.89"));
    assertThat(value.cost()).isNull();
    assertThat(ProductValues.parse("A", "A", "0.01", "0").cost()).isEqualTo(new BigDecimal("0.00"));
    assertThat(ProductValues.parse("A", "A", "1.5", null).price())
        .isEqualTo(new BigDecimal("1.50"));
  }

  @Test
  void invalidMonetaryInputsAreRejectedWithoutRounding() {
    for (String invalid :
        new String[] {
          "0",
          "0.00",
          "-1",
          "1.001",
          "1e2",
          "1,00",
          "+1",
          " 1",
          "1 ",
          "NaN",
          "100000000000000000.00",
          ""
        }) {
      assertThatIllegalArgumentException()
          .as(invalid)
          .isThrownBy(() -> ProductValues.parse("A", "A", invalid, null));
    }
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ProductValues.parse("A", "A", null, null));
    for (String invalid : new String[] {"-0.01", "0.001", "", "1e2"}) {
      assertThatIllegalArgumentException()
          .as(invalid)
          .isThrownBy(() -> ProductValues.parse("A", "A", "1", invalid));
    }
  }

  @Test
  void namesCountUnicodeCodePointsAndSkuRemainsCaseSensitiveAscii() {
    String maximum = "😀".repeat(120);
    assertThat(ProductValues.parse(maximum, "Aa._/-09", "1", null).name()).isEqualTo(maximum);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ProductValues.parse(maximum + "x", "A", "1", null));
    for (String invalid : new String[] {" ", "line\nbreak", "hidden\u202Etext", "\uD800"}) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> ProductValues.parse(invalid, "A", "1", null));
    }
    for (String invalid : new String[] {"", "ç", "a a", "a".repeat(65), "\tX\nY"}) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> ProductValues.parse("A", invalid, "1", null));
    }
  }
}
