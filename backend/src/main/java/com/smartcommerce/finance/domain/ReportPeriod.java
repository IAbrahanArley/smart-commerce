package com.smartcommerce.finance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * A report window [from, to) in the stores' calendar (SC-021, ADR-0015 F1, F8): days start at
 * midnight in America/Sao_Paulo. Named periods end tomorrow at midnight, so today is included.
 */
public record ReportPeriod(
    String key, LocalDate firstDay, LocalDate endDay, Instant from, Instant to) {
  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  /** {@code month} (AAAA-MM) wins over {@code period}; both absent means the current month. */
  public static ReportPeriod of(String period, String month, LocalDate today) {
    if (month != null && !month.isBlank()) {
      if (!month.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException("Invalid month");
      YearMonth value;
      try {
        value = YearMonth.parse(month);
      } catch (DateTimeParseException invalid) {
        throw new IllegalArgumentException("Invalid month");
      }
      return days("MONTH:" + value, value.atDay(1), value.plusMonths(1).atDay(1));
    }
    String key = period == null || period.isBlank() ? "THIS_MONTH" : period;
    LocalDate tomorrow = today.plusDays(1);
    return switch (key) {
      case "TODAY" -> days(key, today, tomorrow);
      case "LAST_7_DAYS" -> days(key, today.minusDays(6), tomorrow);
      case "LAST_30_DAYS" -> days(key, today.minusDays(29), tomorrow);
      case "THIS_MONTH" -> days(key, today.withDayOfMonth(1), tomorrow);
      case "LAST_MONTH" ->
          days(key, today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1));
      default -> throw new IllegalArgumentException("Invalid period");
    };
  }

  private static ReportPeriod days(String key, LocalDate first, LocalDate end) {
    return new ReportPeriod(
        key, first, end, first.atStartOfDay(ZONE).toInstant(), end.atStartOfDay(ZONE).toInstant());
  }
}
