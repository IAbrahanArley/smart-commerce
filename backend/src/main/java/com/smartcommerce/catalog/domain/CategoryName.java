package com.smartcommerce.catalog.domain;

import java.util.Locale;

/**
 * A category name (MVP block 1c): trimmed, 1 to 60 characters, one line. {@code key} is the
 * case-insensitive form used for uniqueness within the store.
 */
public record CategoryName(String name, String key) {
  public static CategoryName parse(String raw) {
    // Line breaks and other control characters are refused, never silently joined.
    if (raw == null || raw.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid category name");
    String value = raw.strip().replaceAll(" {2,}", " ");
    if (value.isEmpty() || value.length() > 60)
      throw new IllegalArgumentException("Invalid category name");
    return new CategoryName(value, value.toLowerCase(Locale.ROOT));
  }
}
