package com.smartcommerce.tenant.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TenantServiceTest {
  @Test
  void companyAndStoreNamesPreserveMeaningfulUnicodeAndBoundCodePoints() {
    assertThat(TenantService.name("  Loja São João  ")).isEqualTo("Loja São João");
    String supplementary = "\uD83D\uDED2".repeat(120);
    assertThat(TenantService.name(supplementary)).isEqualTo(supplementary);
    for (String invalid :
        new String[] {"", "   ", "a".repeat(121), "Line\nBreak", "\u200Bhidden", "\uD800"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> TenantService.name(invalid));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> TenantService.name(null));
  }
}
