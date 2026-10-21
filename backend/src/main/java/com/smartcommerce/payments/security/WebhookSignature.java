package com.smartcommerce.payments.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mercado Pago webhook authentication (SC-014, ADR-0013 P5). The {@code x-signature} header is
 * {@code ts=<timestamp>,v1=<hex>}, and {@code v1} is the HMAC-SHA256, keyed with the application's
 * webhook secret, of the manifest {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}.
 *
 * <ul>
 *   <li>The id is accepted as sent or lowercased: the provider's documentation asks for lowercase
 *       alphanumeric ids, and both forms still require the secret.
 *   <li>{@code ts} may be seconds or milliseconds; it must be within {@link #TOLERANCE} of now, so
 *       an old capture cannot be replayed. Replays inside the window are harmless anyway: a payment
 *       is only confirmed by querying the provider.
 *   <li>Comparison is constant-time; malformed input is simply invalid, never an exception.
 * </ul>
 */
public final class WebhookSignature {
  static final Duration TOLERANCE = Duration.ofMinutes(10);
  private static final HexFormat HEX = HexFormat.of();
  private final byte[] secret;

  public WebhookSignature(String secret) {
    if (secret == null || secret.strip().length() < 16)
      throw new IllegalStateException("payments.mercado-pago.webhook-secret is too short");
    this.secret = secret.strip().getBytes(StandardCharsets.UTF_8);
  }

  public boolean valid(String header, String requestId, String dataId, Instant now) {
    if (header == null || header.length() > 300 || dataId == null || requestId == null)
      return false;
    if (!dataId.matches("[A-Za-z0-9_-]{1,64}") || !requestId.matches("[A-Za-z0-9_.:-]{1,128}"))
      return false;
    String ts = null;
    String v1 = null;
    for (String part : header.split(",")) {
      String[] pair = part.strip().split("=", 2);
      if (pair.length != 2) return false;
      if ("ts".equals(pair[0])) ts = pair[1];
      else if ("v1".equals(pair[0])) v1 = pair[1];
    }
    if (ts == null || v1 == null || !ts.matches("[0-9]{1,13}") || !v1.matches("[0-9a-f]{64}"))
      return false;
    long value = Long.parseLong(ts);
    Instant signedAt =
        ts.length() > 10 ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
    if (Duration.between(signedAt, now).abs().compareTo(TOLERANCE) > 0) return false;
    byte[] expected = HEX.parseHex(v1);
    boolean asSent = MessageDigest.isEqual(expected, hmac(manifest(dataId, requestId, ts)));
    boolean lower =
        MessageDigest.isEqual(
            expected, hmac(manifest(dataId.toLowerCase(Locale.ROOT), requestId, ts)));
    return asSent | lower;
  }

  static String manifest(String dataId, String requestId, String ts) {
    return "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
  }

  byte[] hmac(String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
      throw new IllegalStateException("HmacSHA256 unavailable");
    }
  }

  @Override
  public String toString() {
    return "WebhookSignature[redacted]";
  }
}
