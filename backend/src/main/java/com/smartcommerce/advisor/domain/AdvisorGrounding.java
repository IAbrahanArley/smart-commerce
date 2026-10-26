package com.smartcommerce.advisor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * The number check of ADR-0016 A5: every number and date an answer cites must come from the tool
 * results of that question (or from the question itself). Numbers are read in Brazilian notation
 * ("R$ 1.234,56", "33,3%") and accepted when a source value rounds to them at the precision shown;
 * signs are ignored ("reembolsos de R$ 20,00" for -20.00), and a percentage may also be a fraction
 * times 100. Dates are compared as dd/MM or dd/MM/yyyy. Anything else rejects the answer.
 */
public final class AdvisorGrounding {
  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
  private static final Pattern ANSWER_DATE =
      Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?(?![\\d/])");
  private static final Pattern ANSWER_NUMBER = Pattern.compile("\\d(?:[\\d.,]*\\d)?(\\s?%)?");
  private static final Pattern SOURCE_NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
  private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:\\.\\d{3})+");
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final Set<BigDecimal> numbers = new HashSet<>();
  private final Set<String> dates = new HashSet<>();

  /** Adds every number and date found in a tool result. */
  public AdvisorGrounding addResult(JsonNode node) {
    if (node == null || node.isNull()) return this;
    if (node.isNumber()) numbers.add(node.decimalValue().abs());
    else if (node.isString()) addSourceText(node.asString());
    else if (node.isContainer()) for (JsonNode child : node.values()) addResult(child);
    return this;
  }

  /**
   * Adds numbers of free text the owner or the platform wrote (the question, tool descriptions).
   */
  public AdvisorGrounding addText(String text) {
    if (text == null) return this;
    Matcher matcher = ANSWER_NUMBER.matcher(text);
    while (matcher.find()) {
      BigDecimal value = parse(matcher.group().replace("%", "").strip());
      if (value != null) numbers.add(value.abs());
    }
    return this;
  }

  /** Whether every number and date of {@code answer} has a source. */
  public boolean grounded(String answer) {
    Matcher date = ANSWER_DATE.matcher(answer);
    StringBuilder rest = new StringBuilder();
    while (date.find()) {
      String key = key(date.group(1), date.group(2), date.group(3));
      if (key == null || !dates.contains(key)) return false;
      date.appendReplacement(rest, " ");
    }
    date.appendTail(rest);
    Matcher number = ANSWER_NUMBER.matcher(rest);
    while (number.find()) {
      boolean percent = number.group(1) != null;
      BigDecimal value = parse(number.group().replace("%", "").strip());
      if (value == null || !sourced(value, percent)) return false;
    }
    return true;
  }

  private boolean sourced(BigDecimal shown, boolean percent) {
    int scale = Math.max(shown.scale(), 0);
    for (BigDecimal source : numbers) {
      if (source.setScale(scale, RoundingMode.HALF_UP).compareTo(shown) == 0) return true;
      if (percent
          && source.compareTo(BigDecimal.ONE) <= 0
          && source.multiply(HUNDRED).setScale(scale, RoundingMode.HALF_UP).compareTo(shown) == 0)
        return true;
    }
    return false;
  }

  private void addSourceText(String text) {
    if (ISO_DATE.matcher(text).lookingAt()) {
      LocalDate day = date(text);
      if (day != null) {
        // Only the year stands alone ("outubro de 2026"); a lone day or month would ground any
        // small number.
        addDate(day);
        numbers.add(BigDecimal.valueOf(day.getYear()));
        return;
      }
    }
    // Machine text: amounts like "150.00", product names like "Tênis 42".
    Matcher matcher = SOURCE_NUMBER.matcher(text);
    while (matcher.find()) numbers.add(new BigDecimal(matcher.group().replace(',', '.')));
  }

  private void addDate(LocalDate day) {
    dates.add(String.format("%02d/%02d", day.getDayOfMonth(), day.getMonthValue()));
    dates.add(
        String.format("%02d/%02d/%d", day.getDayOfMonth(), day.getMonthValue(), day.getYear()));
  }

  /** An ISO date, or an instant read on the São Paulo calendar (both days are accepted). */
  private LocalDate date(String text) {
    try {
      if (text.length() == 10) return LocalDate.parse(text);
      Instant instant = Instant.parse(text);
      addDate(LocalDate.ofInstant(instant, java.time.ZoneOffset.UTC));
      return LocalDate.ofInstant(instant, SAO_PAULO);
    } catch (DateTimeParseException invalid) {
      return null;
    }
  }

  private static String key(String day, String month, String year) {
    int d = Integer.parseInt(day);
    int m = Integer.parseInt(month);
    if (d < 1 || d > 31 || m < 1 || m > 12) return null;
    return year == null
        ? String.format("%02d/%02d", d, m)
        : String.format("%02d/%02d/%s", d, m, year);
  }

  /**
   * Brazilian notation first: a comma is the decimal mark and dots group thousands. Without a
   * comma, dots in groups of three are thousands ("1.234"); otherwise a dot is a decimal mark
   * ("150.00"), as machine text would show it.
   */
  static BigDecimal parse(String token) {
    String digits;
    if (token.contains(",")) {
      if (token.indexOf(',') != token.lastIndexOf(',')) return null;
      String integer = token.substring(0, token.indexOf(','));
      if (integer.contains(".") && !THOUSANDS.matcher(integer).matches()) return null;
      digits = integer.replace(".", "") + "." + token.substring(token.indexOf(',') + 1);
    } else if (THOUSANDS.matcher(token).matches()) digits = token.replace(".", "");
    else if (token.indexOf('.') == token.lastIndexOf('.')) digits = token;
    else return null;
    try {
      return new BigDecimal(digits);
    } catch (NumberFormatException invalid) {
      return null;
    }
  }
}
