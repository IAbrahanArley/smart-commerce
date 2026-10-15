package com.smartcommerce.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical storage representation, never rendered in diagnostics. This validates structure and
 * security bounds, not registry availability: callers persist only hashes from the selected
 * encoder. Reading a value never enables its cost/version as an encoder fallback.
 */
public final class PasswordHash {
  private static final Pattern FORMAT =
      Pattern.compile("\\{pbkdf2-sha256-nfc-i([1-9][0-9]{5,6})-s16-l256-v1\\}[0-9a-f]{96}");
  private final String encoded;
  private final int iterations;

  public PasswordHash(String encoded) {
    var matcher = FORMAT.matcher(Objects.requireNonNull(encoded, "Password hash is required"));
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unsupported password hash representation");
    }
    int iterations = Integer.parseInt(matcher.group(1));
    if (iterations < IdentityPasswordEncoder.MIN_ITERATIONS || iterations > 2_000_000) {
      throw new IllegalArgumentException("Unsupported password hash parameters");
    }
    this.encoded = encoded;
    this.iterations = iterations;
  }

  public String encoded() {
    return encoded;
  }

  public int iterations() {
    return iterations;
  }

  @Override
  public String toString() {
    return "PasswordHash[REDACTED]";
  }
}
