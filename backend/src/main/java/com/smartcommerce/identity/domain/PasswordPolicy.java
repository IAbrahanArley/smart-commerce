package com.smartcommerce.identity.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Establishment policy; verification deliberately does not reapply a changing blocklist. */
public final class PasswordPolicy {
  public static final String BLOCKLIST_VERSION = "sc-local-v1";
  private static final int MAX_RAW_UTF16_UNITS = 1024;
  private final Set<String> blocklist;

  public PasswordPolicy() {
    var stream = PasswordPolicy.class.getResourceAsStream("/identity/password-blocklist-v1.txt");
    if (stream == null) {
      throw new IllegalStateException("Password blocklist is unavailable");
    }
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      blocklist =
          reader
              .lines()
              .filter(line -> !line.isBlank() && !line.startsWith("#"))
              .map(line -> Normalizer.normalize(line, Normalizer.Form.NFC).toLowerCase(Locale.ROOT))
              .collect(Collectors.toUnmodifiableSet());
      if (blocklist.isEmpty()) {
        throw new IllegalStateException("Password blocklist is empty");
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Password blocklist cannot be loaded");
    }
  }

  public String normalizeAndValidate(String raw) {
    String normalized = normalizeForVerification(raw);
    int length = normalized.codePointCount(0, normalized.length());
    if (length < 15 || length > 128) {
      throw new IllegalArgumentException("Password must contain 15 to 128 Unicode code points");
    }
    if (normalized.isBlank() || blocklist.contains(normalized.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Password is blocked by local policy");
    }
    return normalized;
  }

  public String normalizeForVerification(String raw) {
    if (raw == null || raw.length() > MAX_RAW_UTF16_UNITS) {
      throw new IllegalArgumentException("Invalid password input");
    }
    requireWellFormedUnicode(raw);
    String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC);
    if (normalized.codePointCount(0, normalized.length()) > 128) {
      throw new IllegalArgumentException("Invalid password input");
    }
    return normalized;
  }

  static void requireWellFormedUnicode(String value) {
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (++index == value.length() || !Character.isLowSurrogate(value.charAt(index))) {
          throw new IllegalArgumentException("Invalid Unicode input");
        }
      } else if (Character.isLowSurrogate(current)) {
        throw new IllegalArgumentException("Invalid Unicode input");
      }
    }
  }
}
