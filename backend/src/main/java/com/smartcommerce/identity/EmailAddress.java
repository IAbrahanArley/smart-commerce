package com.smartcommerce.identity;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/** Explicit initial mailbox contract: ASCII dot-atom local-part and JDK IDNA domain. */
public final class EmailAddress {
  private static final Pattern LOCAL =
      Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*");
  private final String original;
  private final String comparisonKey;

  private EmailAddress(String original, String comparisonKey) {
    this.original = original;
    this.comparisonKey = comparisonKey;
  }

  public static EmailAddress parse(String value) {
    if (value == null || value.length() > 1024) {
      throw invalid();
    }
    int separator = value.indexOf('@');
    if (separator < 1 || separator != value.lastIndexOf('@')) {
      throw invalid();
    }
    String local = value.substring(0, separator);
    String domain = value.substring(separator + 1);
    if (local.length() > 64 || !LOCAL.matcher(local).matches() || domain.isEmpty()) {
      throw invalid();
    }
    // Reject invalid UTF-16 before JDK IDNA; no implicit trimming or trailing root dot.
    PasswordPolicy.requireWellFormedUnicode(domain);
    if (domain
        .codePoints()
        .anyMatch(point -> Character.isWhitespace(point) || Character.isISOControl(point))) {
      throw invalid();
    }
    String ascii;
    try {
      ascii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
    if (ascii.isEmpty() || ascii.endsWith(".") || ascii.length() > 253) {
      throw invalid();
    }
    for (String label : ascii.split("\\.", -1)) {
      if (label.isEmpty()) {
        throw invalid();
      }
      if (label.startsWith("xn--")) {
        String decoded = IDN.toUnicode(label, IDN.USE_STD3_ASCII_RULES);
        if (decoded.equals(label)
            || !IDN.toASCII(decoded, IDN.USE_STD3_ASCII_RULES).equalsIgnoreCase(label)) {
          throw invalid();
        }
      }
    }
    String key = local + "@" + ascii;
    if (key.length() > 254) {
      throw invalid();
    }
    return new EmailAddress(value, key);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid email address");
  }

  public String original() {
    return original;
  }

  public String comparisonKey() {
    return comparisonKey;
  }

  @Override
  public String toString() {
    return "EmailAddress[REDACTED]";
  }
}
