package com.smartcommerce.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.content.domain.CompanyInfo;
import org.junit.jupiter.api.Test;

/** SC-043: company details are normalized and the CNPJ check digits verified. */
class CompanyInfoTest {
  private static CompanyInfo cnpj(String value) {
    return CompanyInfo.of(null, null, value, null, null, null, null, null);
  }

  @Test
  void acceptsAValidCnpjInAnyFormatting() {
    assertThat(cnpj("11.222.333/0001-81").cnpj()).isEqualTo("11222333000181");
    assertThat(cnpj("11222333000181").cnpj()).isEqualTo("11222333000181");
    assertThat(cnpj("  ").cnpj()).isNull();
  }

  @Test
  void refusesWrongCheckDigitsRepeatedDigitsAndOtherShapes() {
    for (String invalid :
        new String[] {"11.222.333/0001-82", "11111111111111", "1122233300018", "11a22233300018"})
      assertThatThrownBy(() -> cnpj(invalid))
          .as(invalid)
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void normalizesPhoneHandlesAndText() {
    CompanyInfo info =
        CompanyInfo.of(
            "  Frete grátis   acima de R$ 200  ",
            "Loja Exemplo LTDA",
            null,
            "(83) 3333-4444",
            "Rua A, 10",
            "https://www.facebook.com/loja.exemplo/",
            "@loja_exemplo",
            "https://www.youtube.com/@LojaExemplo");
    assertThat(info.announcement()).isEqualTo("Frete grátis acima de R$ 200");
    assertThat(info.phone()).isEqualTo("558333334444");
    assertThat(info.facebook()).isEqualTo("loja.exemplo");
    assertThat(info.tiktok()).isEqualTo("loja_exemplo");
    assertThat(info.youtube()).isEqualTo("LojaExemplo");
    for (String[] invalid :
        new String[][] {{"Linha\nquebrada", null}, {null, "+1 555 123 4567"}, {null, "abc"}})
      assertThatThrownBy(
              () -> CompanyInfo.of(invalid[0], null, null, invalid[1], null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> CompanyInfo.of(null, null, null, null, null, "javascript:x", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
