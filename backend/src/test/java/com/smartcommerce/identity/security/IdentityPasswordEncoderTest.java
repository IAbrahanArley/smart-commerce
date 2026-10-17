package com.smartcommerce.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

class IdentityPasswordEncoderTest {
  private final IdentityPasswordEncoder encoder = new IdentityPasswordEncoder();

  @Test
  void independentlyVerifiesActualAlgorithmIterationsSaltAndOutput() throws Exception {
    String password = "Synthetic unique password é";
    String encoded = encoder.encode(password);
    assertThat(encoded).startsWith("{" + IdentityPasswordEncoder.CURRENT_ID + "}");
    byte[] payload = HexFormat.of().parseHex(encoded.substring(encoded.indexOf('}') + 1));
    assertThat(payload).hasSize(48);
    var spec = new PBEKeySpec(password.toCharArray(), Arrays.copyOf(payload, 16), 600_000, 256);
    try {
      byte[] independent =
          SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
      assertThat(Arrays.copyOfRange(payload, 16, 48)).isEqualTo(independent);
    } finally {
      spec.clearPassword();
    }
  }

  @Test
  void generatesDistinctSaltsAndMatchesCorrectNormalizedPassword() {
    String decomposed = "Synthetic Cafe\u0301 password";
    String first = encoder.encode(decomposed);
    String second = encoder.encode(decomposed);
    assertThat(first).isNotEqualTo(second);
    assertThat(encoder.matches("Synthetic Café password", first)).isTrue();
    assertThat(encoder.matches("Different synthetic password", first)).isFalse();
    assertThat(encoder.matches(decomposed + " ", first)).isFalse();
    assertThat(encoder.upgradeEncoding(first)).isFalse();
  }

  @Test
  void supportsExplicitHigherCostAndHistoricalHashWithoutSilentDowngrade() {
    String password = "Future version synthetic password";
    String historical = encoder.encode(password);
    var upgraded = new IdentityPasswordEncoder(600_001);
    assertThat(upgraded.matches(password, historical)).isTrue();
    assertThat(upgraded.upgradeEncoding(historical)).isTrue();
    String replacement = upgraded.encode(password);
    assertThat(replacement)
        .startsWith("{" + IdentityPasswordEncoder.idForIterations(600_001) + "}");
    assertThat(upgraded.matches(password, replacement)).isTrue();
    assertThat(upgraded.upgradeEncoding(replacement)).isFalse();
    assertThat(encoder.matches(password, replacement)).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> new IdentityPasswordEncoder(599_999));
  }

  @Test
  void malformedUnknownAndPlaintextHashesFailClosed() {
    for (String candidate :
        new String[] {
          null,
          "",
          "plaintext",
          "{noop}Synthetic unique password",
          "{pbkdf2}00",
          "{unknown}" + "00".repeat(48),
          "{" + IdentityPasswordEncoder.CURRENT_ID + "}00",
          "{" + IdentityPasswordEncoder.CURRENT_ID + "}" + "zz".repeat(48)
        }) {
      assertThat(encoder.matches("Synthetic unique password", candidate)).isFalse();
      assertThat(encoder.upgradeEncoding(candidate)).isFalse();
    }
    String valid = encoder.encode("Synthetic unique password");
    assertThat(encoder.matches(null, valid)).isFalse();
    assertThat(encoder.matches("\uD800" + "x".repeat(20), valid)).isFalse();
    assertThat(encoder.matches("x".repeat(1_000_000), valid)).isFalse();
  }

  @Test
  void passwordThatLaterBecomesBlockedCanStillVerifyItsExistingHash() throws Exception {
    String password = "passwordpassword";
    byte[] salt = new byte[16];
    Arrays.fill(salt, (byte) 37);
    var spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
    try {
      byte[] derived =
          SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
      String historical =
          "{"
              + IdentityPasswordEncoder.CURRENT_ID
              + "}"
              + HexFormat.of().formatHex(salt)
              + HexFormat.of().formatHex(derived);
      assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(password));
      assertThat(encoder.matches(password, historical)).isTrue();
    } finally {
      spec.clearPassword();
    }
  }
}
