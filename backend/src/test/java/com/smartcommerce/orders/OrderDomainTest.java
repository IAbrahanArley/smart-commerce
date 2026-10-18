package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.orders.domain.AccessToken;
import com.smartcommerce.orders.domain.CheckoutCommand;
import com.smartcommerce.orders.domain.FulfillmentType;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.request.CheckoutRequest;
import com.smartcommerce.orders.security.ClientIpVerifier;
import com.smartcommerce.orders.services.OrderCheckoutService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class OrderDomainTest {
  private static final String SECRET = "synthetic-forwarding-secret-for-unit-tests-01";
  private static final String VARIANT = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";

  private static String body(String items, String fulfillment, String phone, String total) {
    return """
        {"requestId":"33333333-3333-3333-3333-333333333333","items":%s,"fulfillment":%s,
         "buyer":{"name":" Comprador  Sintético ","email":"Pessoa@Example.test","phone":"%s"},
         "expectedTotal":"%s"}"""
        .formatted(items, fulfillment, phone, total);
  }

  private static CheckoutCommand parse(String json) {
    return CheckoutRequest.parse(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parsesAStrictCheckoutAndNormalizesBuyerAndAddress() {
    var command =
        parse(
            body(
                "[{\"variantId\":\"" + VARIANT + "\",\"quantity\":2}]",
                """
                {"type":"FIXED_SHIPPING","address":{"line1":"Rua A, 1","district":"Centro",
                 "city":"São Paulo","state":"sp","postalCode":"01001-000"}}""",
                "+55 (11) 98888-7777",
                "40.30"));
    assertThat(command.fulfillment()).isEqualTo(FulfillmentType.FIXED_SHIPPING);
    assertThat(command.address().state()).isEqualTo("SP");
    assertThat(command.address().postalCode()).isEqualTo("01001000");
    assertThat(command.address().line2()).isNull();
    assertThat(command.buyer().name()).isEqualTo("Comprador  Sintético");
    assertThat(command.buyer().emailKey()).isEqualTo("pessoa@example.test");
    assertThat(command.buyer().phone()).isEqualTo("+5511988887777");
    assertThat(command.expectedTotal()).isEqualByComparingTo("40.30");
  }

  @Test
  void fingerprintIgnoresLineOrderButChangesWithAnyMeaningfulField() {
    String pickup = "{\"type\":\"PICKUP\"}";
    String ab =
        "[{\"variantId\":\""
            + VARIANT
            + "\",\"quantity\":1},{\"variantId\":\""
            + OTHER
            + "\",\"quantity\":2}]";
    String ba =
        "[{\"variantId\":\""
            + OTHER
            + "\",\"quantity\":2},{\"variantId\":\""
            + VARIANT
            + "\",\"quantity\":1}]";
    String base = parse(body(ab, pickup, "11988887777", "10.00")).fingerprint();
    assertThat(parse(body(ba, pickup, "11988887777", "10.00")).fingerprint()).isEqualTo(base);
    assertThat(parse(body(ab, pickup, "11988887777", "10.0")).fingerprint()).isEqualTo(base);
    assertThat(parse(body(ab, pickup, "11988887778", "10.00")).fingerprint()).isNotEqualTo(base);
    assertThat(parse(body(ab, pickup, "11988887777", "10.01")).fingerprint()).isNotEqualTo(base);
    assertThat(base).matches("[0-9a-f]{64}");
  }

  @Test
  void accessTokensAreRandomAndComparedOnlyThroughTheirHash() {
    AccessToken one = AccessToken.generate();
    AccessToken two = AccessToken.generate();
    assertThat(one.value()).isNotEqualTo(two.value()).matches("[A-Za-z0-9_-]{43}");
    assertThat(one.hash()).isNotEqualTo(one.value()).matches("[0-9a-f]{64}");
    assertThat(AccessToken.matches(one.value(), one.hash())).isTrue();
    assertThat(AccessToken.matches(two.value(), one.hash())).isFalse();
    assertThat(AccessToken.matches(null, one.hash())).isFalse();
    assertThat(AccessToken.matches(one.hash(), one.hash())).isFalse();
    assertThat(one.toString()).doesNotContain(one.value());
  }

  @Test
  void signedClientIpIsAcceptedOnlyWithAFreshValidSignature() throws Exception {
    Instant now = Instant.parse("2026-10-18T12:00:00Z");
    var verifier = new ClientIpVerifier(SECRET, Clock.fixed(now, ZoneOffset.UTC));
    String ts = String.valueOf(now.getEpochSecond());
    String ip = "203.0.113.7";
    assertThat(verifier.resolve("10.0.0.1", ip, ts, hmac(ip + "\n" + ts))).isEqualTo(ip);
    assertThat(verifier.resolve("10.0.0.1", ip, ts, hmac(ip + "\n" + ts).toUpperCase()))
        .isEqualTo(ip);
    String stale = String.valueOf(now.minus(Duration.ofSeconds(61)).getEpochSecond());
    assertThat(verifier.resolve("10.0.0.1", ip, stale, hmac(ip + "\n" + stale)))
        .isEqualTo("10.0.0.1");
    assertThat(verifier.resolve("10.0.0.1", "198.51.100.1", ts, hmac(ip + "\n" + ts)))
        .isEqualTo("10.0.0.1");
    assertThat(verifier.resolve("10.0.0.1", ip, ts, null)).isEqualTo("10.0.0.1");
    assertThat(verifier.resolve("10.0.0.1", "evil<script>", ts, hmac("evil<script>\n" + ts)))
        .isEqualTo("10.0.0.1");
    var unconfigured = new ClientIpVerifier("", Clock.fixed(now, ZoneOffset.UTC));
    assertThat(unconfigured.resolve("10.0.0.1", ip, ts, hmac(ip + "\n" + ts)))
        .isEqualTo("10.0.0.1");
    assertThatThrownBy(() -> new ClientIpVerifier("short", Clock.systemUTC()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkoutIsUnavailableUnlessExplicitlyEnabled() {
    var disabled =
        new OrderCheckoutService(
            false, null, null, null, Clock.systemUTC(), Duration.ofMinutes(30));
    var command =
        parse(
            body(
                "[{\"variantId\":\"" + VARIANT + "\",\"quantity\":1}]",
                "{\"type\":\"PICKUP\"}",
                "11988887777",
                "1.00"));
    assertThatThrownBy(() -> disabled.checkout("loja", command, "127.0.0.1"))
        .isInstanceOf(OrderException.class)
        .hasFieldOrPropertyWithValue("reason", OrderException.Reason.MISSING);
  }

  private static String hmac(String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
  }
}
