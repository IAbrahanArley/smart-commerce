package com.smartcommerce.orders.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Trusted visitor IP for checkout limits (ADR-0012 D4). The storefront-web server signs the visitor
 * IP with a shared secret: {@code HMAC-SHA256(secret, ip + "\n" + epochSeconds)} in hex. Without a
 * configured secret, or with a missing, stale (more than 60 s) or invalid signature, the connection
 * address is used. The IP is only an admission key; it is never logged or stored in clear.
 */
@Component
public class ClientIpVerifier {
  static final int MIN_SECRET_LENGTH = 32;
  private static final long MAX_SKEW_SECONDS = 60;
  private final byte[] secret;
  private final Clock clock;

  public ClientIpVerifier(@Value("${orders.client-ip.secret:}") String secret, Clock clock) {
    if (secret != null && !secret.isBlank() && secret.length() < MIN_SECRET_LENGTH)
      throw new IllegalStateException(
          "orders.client-ip.secret must have at least " + MIN_SECRET_LENGTH + " characters");
    this.secret =
        secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  public String resolve(String connection, String ip, String timestamp, String signature) {
    if (secret == null || ip == null || timestamp == null || signature == null) return connection;
    if (ip.length() > 45 || !ip.matches("[0-9A-Fa-f:.]{2,45}") || !timestamp.matches("[0-9]{1,12}"))
      return connection;
    long seconds = Long.parseLong(timestamp);
    if (Math.abs(clock.instant().getEpochSecond() - seconds) > MAX_SKEW_SECONDS) return connection;
    byte[] expected = sign(ip + "\n" + timestamp).getBytes(StandardCharsets.US_ASCII);
    byte[] presented =
        signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(expected, presented) ? ip : connection;
  }

  String sign(String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
