package com.smartcommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.payments.domain.Pkce;
import com.smartcommerce.payments.security.TokenCipher;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenCipherTest {
  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  private static final String OTHER_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
  private final TokenCipher cipher = TokenCipher.fromBase64(KEY);
  private final UUID tenant = UUID.randomUUID();

  @Test
  void roundTripsWithAFreshNonceEveryTimeAndNoPlaintextInTheCiphertext() {
    var nonces = new HashSet<String>();
    for (int i = 0; i < 200; i++) {
      byte[] sealed = cipher.encrypt("APP_USR-synthetic-token", tenant, "mp-access-token");
      assertThat(sealed[0]).isEqualTo((byte) TokenCipher.KEY_VERSION);
      assertThat(nonces.add(Base64.getEncoder().encodeToString(slice(sealed, 1, 13)))).isTrue();
      assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("APP_USR");
      assertThat(cipher.decrypt(sealed, tenant, "mp-access-token"))
          .isEqualTo("APP_USR-synthetic-token");
    }
  }

  @Test
  void ciphertextIsBoundToTenantPurposeKeyAndIntegrity() {
    byte[] sealed = cipher.encrypt("TG-refresh", tenant, "mp-refresh-token");
    assertThatThrownBy(() -> cipher.decrypt(sealed, UUID.randomUUID(), "mp-refresh-token"))
        .hasMessage("Unreadable token ciphertext");
    assertThatThrownBy(() -> cipher.decrypt(sealed, tenant, "mp-access-token"))
        .hasMessage("Unreadable token ciphertext");
    assertThatThrownBy(
            () -> TokenCipher.fromBase64(OTHER_KEY).decrypt(sealed, tenant, "mp-refresh-token"))
        .hasMessage("Unreadable token ciphertext");
    byte[] tampered = sealed.clone();
    tampered[tampered.length - 1] ^= 1;
    assertThatThrownBy(() -> cipher.decrypt(tampered, tenant, "mp-refresh-token"))
        .hasMessage("Unreadable token ciphertext")
        .hasMessageNotContaining("TG-refresh");
    assertThatThrownBy(() -> cipher.decrypt(new byte[5], tenant, "mp-refresh-token"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keyMustBeBase64ForExactly32Bytes() {
    assertThatThrownBy(() -> TokenCipher.fromBase64("")).hasMessageContaining("32 bytes");
    assertThatThrownBy(() -> TokenCipher.fromBase64("not base64!")).hasMessageContaining("Base64");
    assertThatThrownBy(
            () -> TokenCipher.fromBase64(Base64.getEncoder().encodeToString(new byte[16])))
        .hasMessageContaining("32 bytes");
  }

  @Test
  void pkceFollowsRfc7636AndRandomValuesHaveTheExpectedShape() {
    // RFC 7636, appendix B.
    assertThat(Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    var values = new HashSet<String>();
    for (int i = 0; i < 100; i++) {
      String value = Pkce.randomValue();
      assertThat(Pkce.wellFormed(value)).isTrue();
      assertThat(values.add(value)).isTrue();
    }
    assertThat(Pkce.wellFormed(null)).isFalse();
    assertThat(Pkce.wellFormed("short")).isFalse();
    assertThat(Pkce.wellFormed("a".repeat(42) + "=")).isFalse();
    assertThat(Pkce.stateHash("x")).hasSize(32);
  }

  private static byte[] slice(byte[] source, int from, int to) {
    byte[] out = new byte[to - from];
    System.arraycopy(source, from, out, 0, out.length);
    return out;
  }
}
