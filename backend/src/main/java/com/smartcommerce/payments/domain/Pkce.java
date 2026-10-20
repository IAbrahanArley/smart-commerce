package com.smartcommerce.payments.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OAuth state and PKCE values (RFC 7636, S256). The state and the verifier are 256-bit random
 * values in Base64URL (43 characters, inside the 43–128 range the provider requires).
 */
public final class Pkce {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

  private Pkce() {}

  public static String randomValue() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return URL.encodeToString(bytes);
  }

  /** BASE64URL(SHA-256(ASCII(verifier))). */
  public static String challenge(String verifier) {
    return URL.encodeToString(sha256(verifier));
  }

  /** The stored form of a state: its SHA-256, so the table never holds a usable state. */
  public static byte[] stateHash(String state) {
    return sha256(state);
  }

  /** Shape check before any lookup: exactly what {@link #randomValue()} produces. */
  public static boolean wellFormed(String value) {
    return value != null && value.matches("[A-Za-z0-9_-]{43}");
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
