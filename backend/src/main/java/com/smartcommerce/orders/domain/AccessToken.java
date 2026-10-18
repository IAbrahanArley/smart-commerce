package com.smartcommerce.orders.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Buyer access token: 256 random bits, returned once at creation; only its SHA-256 is stored, and
 * comparisons are constant-time (ADR-0012 §3).
 */
public record AccessToken(String value, String hash) {
  private static final SecureRandom RANDOM = new SecureRandom();

  public static AccessToken generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new AccessToken(value, CheckoutCommand.sha256(value));
  }

  public static boolean matches(String presented, String storedHash) {
    if (presented == null || storedHash == null || !presented.matches("[A-Za-z0-9_-]{43}"))
      return false;
    return MessageDigest.isEqual(
        CheckoutCommand.sha256(presented).getBytes(StandardCharsets.US_ASCII),
        storedHash.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public String toString() {
    return "AccessToken[redacted]";
  }
}
