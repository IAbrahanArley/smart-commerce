package com.smartcommerce.customers.domain;

import com.smartcommerce.identity.domain.EmailAddress;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Validated store customer data (SC-055). Name: one line of 2 to 120 characters. Phone: 10 to 15
 * digits, an optional leading "+", spaces, dots, dashes and parentheses dropped. E-mail optional,
 * keyed in lower case (ADR-0021 R1). Note: one line of up to 500 characters. Credit limit: decimal
 * text with up to two decimals, blank for no limit.
 */
public final class CustomerInput {
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private CustomerInput() {}

  /** The contact and credit data of a customer; {@code emailKey} is null without an e-mail. */
  public record Data(
      String name,
      String phone,
      String email,
      String emailKey,
      String note,
      BigDecimal creditLimit) {

    public static Data of(
        String name, String phone, String email, String note, String creditLimit) {
      String cleanEmail = email == null || email.isBlank() ? null : email.strip();
      EmailAddress address = cleanEmail == null ? null : EmailAddress.parse(cleanEmail);
      return new Data(
          line(name, 2, 120),
          digits(phone),
          address == null ? null : address.original(),
          address == null ? null : address.comparisonKey().toLowerCase(Locale.ROOT),
          note == null || note.isBlank() ? null : line(note, 1, 500),
          amount(creditLimit));
    }

    /** Whether only name and phone are given: what an employee may set. */
    public boolean contactOnly() {
      return email == null && note == null && creditLimit == null;
    }

    public boolean sameAs(StoreCustomer stored) {
      return stored.name().equals(name)
          && stored.phone().equals(phone)
          && Objects.equals(stored.email(), email)
          && Objects.equals(stored.note(), note)
          && (stored.creditLimit() == null
              ? creditLimit == null
              : creditLimit != null && stored.creditLimit().compareTo(creditLimit) == 0);
    }
  }

  public record Create(UUID requestId, Data data) {
    public static Create of(String requestId, Data data) {
      return new Create(id(requestId), data);
    }
  }

  /** A search of at least 2 characters: name, e-mail or, with 4 digits or more, phone. */
  public static String search(String value) {
    String clean = value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    int length = clean.codePointCount(0, clean.length());
    if (length < 2 || length > 120 || clean.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid search");
    return clean;
  }

  static String digits(String value) {
    String digits = value == null ? null : value.replaceAll("[\\s().-]", "");
    if (digits == null || !digits.matches("\\+?[0-9]{10,15}"))
      throw new IllegalArgumentException("Invalid phone");
    return digits;
  }

  static BigDecimal amount(String value) {
    if (value == null || value.isBlank()) return null;
    if (!value.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid amount");
    return new BigDecimal(value).setScale(2);
  }

  static UUID id(String value) {
    if (value == null || !value.matches(UUID_PATTERN))
      throw new IllegalArgumentException("Invalid id");
    return UUID.fromString(value);
  }

  /** One line of {@code min} to {@code max} characters, NFC, no control or invisible characters. */
  static String line(String value, int min, int max) {
    if (value == null || value.length() > max * 4)
      throw new IllegalArgumentException("Invalid text");
    String clean = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    int length = clean.codePointCount(0, clean.length());
    if (length < min
        || length > max
        || clean
            .codePoints()
            .anyMatch(
                code ->
                    Character.isISOControl(code)
                        || Character.getType(code) == Character.FORMAT
                        || Character.getType(code) == Character.LINE_SEPARATOR
                        || Character.getType(code) == Character.PARAGRAPH_SEPARATOR
                        || Character.getType(code) == Character.SURROGATE))
      throw new IllegalArgumentException("Invalid text");
    return clean;
  }
}
