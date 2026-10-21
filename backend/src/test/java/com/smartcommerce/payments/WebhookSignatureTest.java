package com.smartcommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.payments.security.WebhookSignature;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** SC-014: x-signature validation, independent of Spring and of the network. */
class WebhookSignatureTest {
  private static final String SECRET = "synthetic-webhook-secret-0014";
  private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
  private final WebhookSignature signature = new WebhookSignature(SECRET);

  @Test
  void acceptsTheDocumentedManifestInSecondsOrMillisecondsAndALowercasedId() {
    String ts = String.valueOf(NOW.getEpochSecond());
    assertThat(signature.valid(header(ts, "ORD01ABC", "req-1"), "req-1", "ORD01ABC", NOW)).isTrue();
    String ms = String.valueOf(NOW.toEpochMilli());
    assertThat(signature.valid(header(ms, "ORD01ABC", "req-1"), "req-1", "ORD01ABC", NOW)).isTrue();
    // Signed over the lowercased id (documented for alphanumeric ids), received as sent.
    assertThat(signature.valid(header(ts, "ord01abc", "req-1"), "req-1", "ORD01ABC", NOW)).isTrue();
    assertThat(
            signature.valid(
                " ts=" + ts + " , v1=" + v1(ts, "ORD01ABC", "req-1"), "req-1", "ORD01ABC", NOW))
        .isTrue();
  }

  @Test
  void rejectsTamperingStaleTimestampsOtherSecretsAndMalformedInput() {
    String ts = String.valueOf(NOW.getEpochSecond());
    String good = header(ts, "ORD01ABC", "req-1");
    assertThat(signature.valid(good, "req-1", "ORD01ABD", NOW)).isFalse(); // other order
    assertThat(signature.valid(good, "req-2", "ORD01ABC", NOW)).isFalse(); // other request
    assertThat(signature.valid(good, "req-1", "ORD01ABC", NOW.plusSeconds(601))).isFalse();
    assertThat(signature.valid(good, "req-1", "ORD01ABC", NOW.minusSeconds(601))).isFalse();
    assertThat(
            new WebhookSignature("another-synthetic-secret-0014")
                .valid(good, "req-1", "ORD01ABC", NOW))
        .isFalse();
    for (String header :
        new String[] {
          null,
          "",
          "ts=" + ts,
          "v1=" + v1(ts, "ORD01ABC", "req-1"),
          "ts=abc,v1=" + v1(ts, "ORD01ABC", "req-1"),
          "ts=" + ts + ",v1=" + "0".repeat(64),
          "ts=" + ts + ",v1=" + v1(ts, "ORD01ABC", "req-1").toUpperCase(),
          "ts=" + ts + ",v1=zz",
          "garbage",
          "ts=" + ts + ",v1=" + v1(ts, "ORD01ABC", "req-1") + ",x".repeat(200)
        })
      assertThat(signature.valid(header, "req-1", "ORD01ABC", NOW))
          .as(String.valueOf(header))
          .isFalse();
    assertThat(signature.valid(good, null, "ORD01ABC", NOW)).isFalse();
    assertThat(signature.valid(good, "req-1", null, NOW)).isFalse();
    assertThat(signature.valid(good, "req-1", "ORD/../x", NOW)).isFalse();
    assertThat(signature.toString()).doesNotContain(SECRET);
    assertThatThrownBy(() -> new WebhookSignature("short"))
        .isInstanceOf(IllegalStateException.class);
  }

  static String header(String ts, String id, String request) {
    return "ts=" + ts + ",v1=" + v1(ts, id, request);
  }

  static String v1(String ts, String id, String request) {
    return sign(SECRET, "id:" + id + ";request-id:" + request + ";ts:" + ts + ";");
  }

  static String sign(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
