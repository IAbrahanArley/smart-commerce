package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Validated input of an account movement (SC-050, ADR-0019 C2): BRL with 2 decimal places, never a
 * float; a calendar day in São Paulo not before 2000 nor after today. Adjustments are signed and
 * need a reason; transfers are positive and the note is optional.
 */
public final class MovementInput {
  private static final LocalDate FLOOR = LocalDate.of(2000, 1, 1);
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private MovementInput() {}

  public record Adjustment(
      UUID requestId, BigDecimal amount, LocalDate occurredOn, String reason) {}

  public record Transfer(
      UUID requestId, UUID from, UUID to, BigDecimal amount, LocalDate occurredOn, String note) {}

  public record NewAccount(UUID requestId, String type, String name) {}

  public static Adjustment adjustment(
      String requestId, String amount, String occurredOn, String reason, LocalDate today) {
    if (amount == null || !amount.matches("-?[0-9]{1,15}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid amount");
    BigDecimal value = new BigDecimal(amount).setScale(2);
    if (value.signum() == 0) throw new IllegalArgumentException("Invalid amount");
    return new Adjustment(
        id(requestId), value, day(occurredOn, today), ExpenseInput.line(reason, 200));
  }

  public static Transfer transfer(
      String requestId,
      String from,
      String to,
      String amount,
      String occurredOn,
      String note,
      LocalDate today) {
    if (amount == null || !amount.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid amount");
    BigDecimal value = new BigDecimal(amount).setScale(2);
    if (value.signum() <= 0) throw new IllegalArgumentException("Invalid amount");
    String text = note == null || note.isBlank() ? null : ExpenseInput.line(note, 200);
    return new Transfer(id(requestId), id(from), id(to), value, day(occurredOn, today), text);
  }

  public static NewAccount account(String requestId, String type, String name) {
    if (!"CASH".equals(type) && !"BANK".equals(type))
      throw new IllegalArgumentException("Invalid account type");
    return new NewAccount(id(requestId), type, ExpenseInput.line(name, 60));
  }

  private static UUID id(String value) {
    if (value == null || !value.matches(UUID_PATTERN))
      throw new IllegalArgumentException("Invalid id");
    return UUID.fromString(value);
  }

  private static LocalDate day(String value, LocalDate today) {
    LocalDate day;
    try {
      day = value == null ? null : LocalDate.parse(value);
    } catch (java.time.format.DateTimeParseException invalid) {
      day = null;
    }
    if (day == null || day.isBefore(FLOOR) || day.isAfter(today))
      throw new IllegalArgumentException("Invalid date");
    return day;
  }
}
