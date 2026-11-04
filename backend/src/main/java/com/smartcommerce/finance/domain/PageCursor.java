package com.smartcommerce.finance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Page position of a finance list: the last row of the previous page, as the list orders it. Cash
 * sessions use the opening instant and id; payables and statements order by a day first, so they
 * carry it too. Text form "{@code [yyyy-mm-dd_]<ISO-8601 instant>_<uuid>}", validated before any
 * query.
 */
public record PageCursor(LocalDate day, Instant at, UUID id) {
  public static final int PAGE_SIZE = 50;

  public static PageCursor parse(String value, boolean withDay) {
    if (value == null || value.isBlank()) return null;
    String pattern = (withDay ? "\\d{4}-\\d{2}-\\d{2}_" : "") + "[0-9TZ:.\\-]+_[0-9a-f\\-]{36}";
    if (value.length() > 100 || !value.matches(pattern))
      throw new IllegalArgumentException("Invalid cursor");
    String[] parts = value.split("_");
    try {
      return withDay
          ? new PageCursor(
              LocalDate.parse(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2]))
          : new PageCursor(null, Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Invalid cursor", exception);
    }
  }

  public String text() {
    return (day == null ? "" : day + "_") + at + "_" + id;
  }
}
