package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A validated expense or payable (SC-020, SC-051; ADR-0015 F6, ADR-0019 C4). Categories, kinds and
 * recurrences are fixed lists; the amount is positive with at most two decimals; dates are calendar
 * days in America/Sao_Paulo, the payment never in the future and nothing before 2000.
 *
 * <ul>
 *   <li>The due date defaults to the competence date, and the competence to the due date.
 *   <li>With 2 to 24 installments, {@code amount} is the bill's total: each installment falls due a
 *       month after the previous one and counts in the month it falls due (decided on 2026-11-02),
 *       the last one absorbing the cents. Installments are neither recurring nor paid on creation.
 *   <li>Paid on creation means a payment date and the account the money left.
 * </ul>
 */
public record ExpenseInput(
    UUID requestId,
    String category,
    String kind,
    String description,
    BigDecimal amount,
    LocalDate competenceDate,
    LocalDate dueDate,
    String supplier,
    String documentNumber,
    int installments,
    String recurrence,
    LocalDate paidOn,
    UUID paidAccountId) {
  public static final List<String> CATEGORIES =
      List.of("SHIPPING", "PACKAGING", "MARKETING", "RENT", "PEOPLE", "OTHER");
  public static final List<String> KINDS = List.of("FIXED", "VARIABLE");
  public static final List<String> RECURRENCES = List.of("NONE", "MONTHLY");
  public static final int MAX_INSTALLMENTS = 24;
  static final LocalDate FLOOR = LocalDate.of(2000, 1, 1);
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  /** A single expense with no due date, supplier, recurrence or payment account (SC-020 form). */
  public static ExpenseInput of(
      String requestId,
      String category,
      String kind,
      String description,
      String amount,
      String competenceDate,
      String paidOn,
      LocalDate today) {
    return of(
        requestId,
        category,
        kind,
        description,
        amount,
        competenceDate,
        null,
        null,
        null,
        null,
        null,
        paidOn,
        null,
        today);
  }

  public static ExpenseInput of(
      String requestId,
      String category,
      String kind,
      String description,
      String amount,
      String competenceDate,
      String dueDate,
      String supplier,
      String documentNumber,
      Integer installments,
      String recurrence,
      String paidOn,
      String paidAccountId,
      LocalDate today) {
    UUID request = uuid(requestId);
    if (request == null) throw new IllegalArgumentException("Invalid request id");
    if (!listed(CATEGORIES, category)) throw new IllegalArgumentException("Invalid category");
    if (!listed(KINDS, kind)) throw new IllegalArgumentException("Invalid kind");
    BigDecimal value = money(amount);
    if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Invalid amount");
    int count = installments == null ? 1 : installments;
    if (count < 1 || count > MAX_INSTALLMENTS)
      throw new IllegalArgumentException("Invalid installments");
    String repeat = recurrence == null || recurrence.isBlank() ? "NONE" : recurrence;
    if (!listed(RECURRENCES, repeat)) throw new IllegalArgumentException("Invalid recurrence");
    LocalDate limit = today.plusYears(1);
    LocalDate due = blank(dueDate) ? null : date(dueDate);
    if (!blank(dueDate) && (due == null || due.isBefore(FLOOR) || due.isAfter(limit)))
      throw new IllegalArgumentException("Invalid due date");
    LocalDate competence = blank(competenceDate) ? null : date(competenceDate);
    if (!blank(competenceDate)
        && (competence == null || competence.isBefore(FLOOR) || competence.isAfter(limit)))
      throw new IllegalArgumentException("Invalid competence date");
    if (count > 1) {
      // Each installment counts in its own due month: a competence, if sent, is the first due.
      if (due == null || (competence != null && !competence.equals(due)))
        throw new IllegalArgumentException("Invalid installment dates");
      if (!"NONE".equals(repeat) || !blank(paidOn) || !blank(paidAccountId))
        throw new IllegalArgumentException("Invalid installments");
      if (value.compareTo(BigDecimal.valueOf(count, 2)) < 0)
        throw new IllegalArgumentException("Invalid amount");
      competence = due;
    }
    // Either date alone stands for both: a bill counts in the month it falls due unless told.
    if (competence == null) competence = due;
    if (competence == null) throw new IllegalArgumentException("Invalid competence date");
    if (due == null) due = competence;
    LocalDate paid = blank(paidOn) ? null : date(paidOn);
    if (!blank(paidOn) && (paid == null || paid.isBefore(FLOOR) || paid.isAfter(today)))
      throw new IllegalArgumentException("Invalid payment date");
    UUID account = blank(paidAccountId) ? null : uuid(paidAccountId);
    if (!blank(paidAccountId) && account == null)
      throw new IllegalArgumentException("Invalid account");
    if ((paid == null) != (account == null)) throw new IllegalArgumentException("Invalid payment");
    return new ExpenseInput(
        request,
        category,
        kind,
        text(description),
        value,
        competence,
        due,
        optional(supplier, 2, 120),
        optional(documentNumber, 1, 40),
        count,
        repeat,
        paid,
        account);
  }

  public boolean monthly() {
    return "MONTHLY".equals(recurrence);
  }

  /** The amount of each installment, in order: equal cents, the last one absorbing the rest. */
  public List<BigDecimal> installmentAmounts() {
    BigDecimal each =
        amount.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN).setScale(2);
    List<BigDecimal> amounts = new ArrayList<>();
    for (int index = 1; index < installments; index++) amounts.add(each);
    amounts.add(amount.subtract(each.multiply(BigDecimal.valueOf(installments - 1))));
    return amounts;
  }

  /** The expense description: one line of 3 to 200 characters. */
  public static String text(String value) {
    return line(value, 200);
  }

  /** One line of 3 to {@code max} characters, with no control or invisible characters. */
  public static String line(String value, int max) {
    return line(value, 3, max);
  }

  /** One line of {@code min} to {@code max} characters, with no control or invisible characters. */
  public static String line(String value, int min, int max) {
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
    if (length < min || length > max) throw new IllegalArgumentException("Invalid text");
    return clean;
  }

  /** {@code value} cut to at most {@code max} characters (code points), for derived texts. */
  public static String clip(String value, int max) {
    return value.codePointCount(0, value.length()) <= max
        ? value
        : value.substring(0, value.offsetByCodePoints(0, max));
  }

  /** Like {@link #line(String, int, int)}, but absent or blank is no value. */
  public static String optional(String value, int min, int max) {
    return value == null || value.isBlank() ? null : line(value, min, max);
  }

  /** Money as text: up to 15 digits and 2 decimals, never negative; null when malformed. */
  public static BigDecimal money(String value) {
    if (value == null || !value.matches("[0-9]{1,15}(\\.[0-9]{1,2})?")) return null;
    return new BigDecimal(value).setScale(2);
  }

  static LocalDate date(String value) {
    if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException invalid) {
      return null;
    }
  }

  static UUID uuid(String value) {
    return value != null && value.matches(UUID_PATTERN) ? UUID.fromString(value) : null;
  }

  /** Whether {@code value} is one of a fixed list; absent is not (List.of refuses null lookups). */
  public static boolean listed(List<String> list, String value) {
    return value != null && list.contains(value);
  }

  static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Same submission: a retry with the same request id must carry the same expense. For
   * installments, {@code stored} is the first one.
   */
  public boolean sameAs(Expense stored) {
    return category.equals(stored.category())
        && kind.equals(stored.kind())
        && description.equals(stored.description())
        && installmentAmounts().get(0).compareTo(stored.amount()) == 0
        && installments == (stored.installmentCount() == null ? 1 : stored.installmentCount())
        && competenceDate.equals(stored.competenceDate())
        && dueDate.equals(stored.dueDate())
        && Objects.equals(supplier, stored.supplier())
        && Objects.equals(documentNumber, stored.documentNumber())
        && recurrence.equals(stored.recurrence())
        // Paid later through /pay, the bill is still the one this request created.
        && (paidOn == null
            || (paidOn.equals(stored.paidOn()) && paidAccountId.equals(stored.paidAccountId())));
  }
}
