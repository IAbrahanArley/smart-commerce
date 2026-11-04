package com.smartcommerce.orders.domain;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Page position of the counter sale list, newest first: the creation instant and id of the last
 * sale of the previous page. Text form "{@code <ISO-8601 instant>_<uuid>}", validated before any
 * query.
 */
public record CounterSaleCursor(Instant createdAt, UUID id) {
  public static final int PAGE_SIZE = 50;

  public static CounterSaleCursor parse(String value) {
    if (value == null || value.isBlank()) return null;
    if (value.length() > 80 || !value.matches("[0-9TZ:.\\-]+_[0-9a-f\\-]{36}"))
      throw new IllegalArgumentException("Invalid cursor");
    int split = value.lastIndexOf('_');
    try {
      return new CounterSaleCursor(
          Instant.parse(value.substring(0, split)), UUID.fromString(value.substring(split + 1)));
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Invalid cursor", exception);
    }
  }

  public String text() {
    return createdAt + "_" + id;
  }
}
