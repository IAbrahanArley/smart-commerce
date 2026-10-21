package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * A validated manual expense (SC-020, ADR-0015 F6). Categories and kinds are a fixed list; the
 * amount is positive with at most two decimals; dates are calendar days in America/Sao_Paulo, the
 * payment never in the future and never before the competence's year 2000 floor.
 */
public record ExpenseInput(
    UUID requestId,
    String category,
    String kind,
    String description,
    BigDecimal amount,
    LocalDate competenceDate,
    LocalDate paidOn) {
  public static final List<String> CATEGORIES =
      List.of("SHIPPING", "PACKAGING", "MARKETING", "RENT", "PEOPLE", "OTHER");
  public static final List<String> KINDS = List.of("FIXED", "VARIABLE");
  private static final LocalDate FLOOR = LocalDate.of(2000, 1, 1);

  public static ExpenseInput of(
      String requestId,
      String category,
      String kind,
      String description,
      String amount,
      String competenceDate,
      String paidOn,
      LocalDate today) {
    if (requestId == null
        || !requestId.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new IllegalArgumentException("Invalid request id");
    if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("Invalid category");
    if (!KINDS.contains(kind)) throw new IllegalArgumentException("Invalid kind");
    if (amount == null || !amount.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid amount");
    BigDecimal value = new BigDecimal(amount).setScale(2);
    if (value.signum() <= 0) throw new IllegalArgumentException("Invalid amount");
    LocalDate competence = date(competenceDate);
    if (competence == null || competence.isBefore(FLOOR) || competence.isAfter(today.plusYears(1)))
      throw new IllegalArgumentException("Invalid competence date");
    LocalDate paid = paidOn == null || paidOn.isBlank() ? null : date(paidOn);
    if (paidOn != null && !paidOn.isBlank() && paid == null)
      throw new IllegalArgumentException("Invalid payment date");
    if (paid != null && (paid.isBefore(FLOOR) || paid.isAfter(today)))
      throw new IllegalArgumentException("Invalid payment date");
    return new ExpenseInput(
        UUID.fromString(requestId), category, kind, text(description), value, competence, paid);
  }

  /** The expense description: one line of 3 to 200 characters. */
  public static String text(String value) {
    return line(value, 200);
  }

  /** One line of 3 to {@code max} characters, with no control or invisible characters. */
  public static String line(String value, int max) {
    if (value == null
        || value.length() > max * 4
        || value
            .codePoints()
            .anyMatch(
                code ->
                    Character.isISOControl(code)
                        || Character.getType(code) == Character.FORMAT
                        || Character.getType(code) == Character.LINE_SEPARATOR
                        || Character.getType(code) == Character.PARAGRAPH_SEPARATOR))
      throw new IllegalArgumentException("Invalid text");
    String clean = value.strip();
    int length = clean.codePointCount(0, clean.length());
    if (length < 3 || length > max) throw new IllegalArgumentException("Invalid text");
    return clean;
  }

  private static LocalDate date(String value) {
    if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException invalid) {
      return null;
    }
  }

  /** Same submission: a retry with the same request id must carry the same expense. */
  public boolean sameAs(Expense stored) {
    return category.equals(stored.category())
        && kind.equals(stored.kind())
        && description.equals(stored.description())
        && amount.compareTo(stored.amount()) == 0
        && competenceDate.equals(stored.competenceDate())
        && java.util.Objects.equals(paidOn, stored.paidOn());
  }
}
