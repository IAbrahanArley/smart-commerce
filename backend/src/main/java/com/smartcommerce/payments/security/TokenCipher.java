package com.smartcommerce.payments.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for provider secrets at rest (ADR-0013 P3). Layout: key version (1 byte), 96-bit
 * random nonce, ciphertext with the 128-bit tag. The associated data binds each value to its tenant
 * and purpose, so a ciphertext copied to another store or column fails to decrypt. Messages never
 * carry key material or plaintext.
 */
public final class TokenCipher {
  public static final int KEY_VERSION = 1;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  private TokenCipher(byte[] key) {
    this.key = new SecretKeySpec(key, "AES");
  }

  /** {@code base64Key} must decode to exactly 32 bytes (AES-256). */
  public static TokenCipher fromBase64(String base64Key) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.strip());
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("payments.token-encryption-key must be Base64");
    }
    if (decoded.length != 32)
      throw new IllegalStateException("payments.token-encryption-key must decode to 32 bytes");
    return new TokenCipher(decoded);
  }

  public byte[] encrypt(String plaintext, UUID tenant, String purpose) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad(tenant, purpose));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(1 + NONCE_BYTES + sealed.length)
          .put((byte) KEY_VERSION)
          .put(nonce)
          .put(sealed)
          .array();
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Token encryption failed");
    }
  }

  public String decrypt(byte[] stored, UUID tenant, String purpose) {
    if (stored == null
        || stored.length < 1 + NONCE_BYTES + TAG_BITS / 8
        || stored[0] != KEY_VERSION) throw new IllegalStateException("Unreadable token ciphertext");
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, NONCE_BYTES));
      cipher.updateAAD(aad(tenant, purpose));
      byte[] plain = cipher.doFinal(stored, 1 + NONCE_BYTES, stored.length - 1 - NONCE_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Unreadable token ciphertext");
    }
  }

  private static byte[] aad(UUID tenant, String purpose) {
    return ("smart-commerce:" + purpose + ":" + tenant).getBytes(StandardCharsets.UTF_8);
  }
}
