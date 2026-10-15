package com.smartcommerce.identity;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/** Bounded, explicit encoder registry. Unknown versions fail closed, with no fallback. */
public final class IdentityPasswordEncoder implements PasswordEncoder {
  public static final int MIN_ITERATIONS = 600_000;
  public static final String CURRENT_ID = idForIterations(MIN_ITERATIONS);
  private final PasswordPolicy policy = new PasswordPolicy();
  private final DelegatingPasswordEncoder delegate;
  private final Map<String, PasswordEncoder> registry;

  public IdentityPasswordEncoder() {
    this(MIN_ITERATIONS);
  }

  /** Explicit future upgrade/benchmark configuration; historical 600k stays readable. */
  public IdentityPasswordEncoder(int iterations) {
    if (iterations < MIN_ITERATIONS || iterations > 2_000_000) {
      throw new IllegalArgumentException(
          "Iteration count is outside the supported security budget");
    }
    registry = new HashMap<>();
    registry.put(CURRENT_ID, encoder(MIN_ITERATIONS));
    registry.put(idForIterations(iterations), encoder(iterations));
    delegate = new DelegatingPasswordEncoder(idForIterations(iterations), registry);
  }

  public static String idForIterations(int iterations) {
    return "pbkdf2-sha256-nfc-i" + iterations + "-s16-l256-v1";
  }

  private static PasswordEncoder encoder(int iterations) {
    var encoder =
        new Pbkdf2PasswordEncoder(
            "",
            16,
            iterations,
            Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    encoder.setEncodeHashAsBase64(false);
    return encoder;
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return delegate.encode(
        policy.normalizeAndValidate(rawPassword == null ? null : rawPassword.toString()));
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (!recognized(encodedPassword)) {
      return false;
    }
    try {
      return delegate.matches(
          policy.normalizeForVerification(rawPassword == null ? null : rawPassword.toString()),
          encodedPassword);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    return recognized(encodedPassword) && delegate.upgradeEncoding(encodedPassword);
  }

  private boolean recognized(String encoded) {
    if (encoded == null || encoded.length() > 200 || !encoded.startsWith("{")) {
      return false;
    }
    int end = encoded.indexOf('}');
    return end > 1
        && registry.containsKey(encoded.substring(1, end))
        && encoded.substring(end + 1).matches("[0-9a-f]{96}");
  }
}
