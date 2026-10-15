package com.smartcommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void appliesNfcBeforeCountingAndPreservesSpaces() {
    assertThat(policy.normalizeAndValidate("  Cafe\u0301 synthetic password  "))
        .isEqualTo("  Café synthetic password  ");
    assertThat(policy.normalizeAndValidate("á".repeat(15)))
        .isEqualTo(policy.normalizeAndValidate("a\u0301".repeat(15)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> policy.normalizeAndValidate("a\u0301".repeat(14)));
  }

  @Test
  void measuresCodePointsRatherThanUtf16UnitsWithoutTruncation() {
    assertThat(policy.normalizeAndValidate("🦉".repeat(128))).hasSize(256);
    assertThat(policy.normalizeAndValidate("🦉".repeat(15)).codePointCount(0, 30)).isEqualTo(15);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> policy.normalizeAndValidate("🦉".repeat(129)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> policy.normalizeAndValidate("S".repeat(14)));
    assertThat(policy.normalizeAndValidate("S".repeat(128))).hasSize(128);
  }

  @Test
  void rejectsNullUnpairedSurrogatesAndOversizedRawInput() {
    for (String candidate :
        new String[] {
          null, "\uD800" + "x".repeat(15), "x".repeat(15) + "\uDC00", "x".repeat(1_000_000)
        }) {
      assertThatIllegalArgumentException().isThrownBy(() -> policy.normalizeAndValidate(candidate));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> policy.normalizeForVerification(candidate));
    }
  }

  @Test
  void rejectsLocalBlocklistAndBlankPasswordsOnlyAtEstablishment() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> policy.normalizeAndValidate("PASSWORDPASSWORD"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> policy.normalizeAndValidate(" ".repeat(15)));
    assertThat(policy.normalizeForVerification("PASSWORDPASSWORD")).isEqualTo("PASSWORDPASSWORD");
  }
}
