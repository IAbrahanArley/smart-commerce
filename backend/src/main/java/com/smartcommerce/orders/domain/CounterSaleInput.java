package com.smartcommerce.orders.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Validated counter sale requests (SC-053). Money is text with up to two decimals, never negative;
 * a sale has 1 to 100 distinct variants of 1 to 9999 units each.
 */
public final class CounterSaleInput {
  public static final List<String> METHODS =
      List.of("CASH", "PIX", "DEBIT", "CREDIT", "STORE_CREDIT");
  static final int MAX_LINES = 100;
  private static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private CounterSaleInput() {}

  public record Line(UUID variantId, int quantity) {}

  /**
   * A sale: CASH needs the open cash session it goes into; STORE_CREDIT (SC-055) needs the
   * customer, 1 to 12 installments and the first due date; the other methods none of them.
   */
  public record Sale(
      UUID requestId,
      List<Line> lines,
      BigDecimal discount,
      String method,
      UUID cashSessionId,
      UUID customerId,
      Integer installments,
      LocalDate firstDueOn) {
    public static Sale of(
        String requestId, List<Line> lines, String discount, String method, String cashSessionId) {
      return of(requestId, lines, discount, method, cashSessionId, null, null, null);
    }

    public static Sale of(
        String requestId,
        List<Line> lines,
        String discount,
        String method,
        String cashSessionId,
        String customerId,
        Integer installments,
        String firstDueOn) {
      if (method == null || !METHODS.contains(method))
        throw new IllegalArgumentException("Invalid payment method");
      if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES)
        throw new IllegalArgumentException("Invalid items");
      var seen = new HashSet<UUID>();
      for (Line line : lines)
        if (line == null
            || line.variantId() == null
            || line.quantity() < 1
            || line.quantity() > 9999
            || !seen.add(line.variantId())) throw new IllegalArgumentException("Invalid item");
      BigDecimal off =
          discount == null || discount.isBlank() ? BigDecimal.ZERO.setScale(2) : money(discount);
      UUID session = cashSessionId == null || cashSessionId.isBlank() ? null : id(cashSessionId);
      if ("CASH".equals(method) != (session != null))
        throw new IllegalArgumentException("Invalid cash session");
      boolean credit = "STORE_CREDIT".equals(method);
      UUID customer = customerId == null || customerId.isBlank() ? null : id(customerId);
      LocalDate due = date(firstDueOn);
      if (credit != (customer != null)
          || credit != (installments != null)
          || credit != (due != null)
          || (installments != null && (installments < 1 || installments > 12)))
        throw new IllegalArgumentException("Invalid store credit");
      return new Sale(
          id(requestId), List.copyOf(lines), off, method, session, customer, installments, due);
    }
  }

  /** The OWNER's settings; blank fees or days stay unknown. */
  public record Settings(
      BigDecimal staffDiscountLimit,
      UUID pixAccountId,
      BigDecimal debitFeePercent,
      Integer debitReleaseDays,
      BigDecimal creditFeePercent,
      Integer creditReleaseDays) {
    public static Settings of(
        String staffDiscountLimit,
        String pixAccountId,
        String debitFeePercent,
        Integer debitReleaseDays,
        String creditFeePercent,
        Integer creditReleaseDays) {
      BigDecimal limit = percent(staffDiscountLimit, true);
      if (limit == null) limit = BigDecimal.ZERO.setScale(2);
      return new Settings(
          limit,
          pixAccountId == null || pixAccountId.isBlank() ? null : id(pixAccountId),
          percent(debitFeePercent, false),
          days(debitReleaseDays),
          percent(creditFeePercent, false),
          days(creditReleaseDays));
    }
  }

  static LocalDate date(String value) {
    if (value == null || value.isBlank()) return null;
    if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Invalid date");
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException invalid) {
      throw new IllegalArgumentException("Invalid date", invalid);
    }
  }

  static UUID id(String value) {
    if (value == null || !value.matches(UUID_PATTERN))
      throw new IllegalArgumentException("Invalid id");
    return UUID.fromString(value);
  }

  static BigDecimal money(String value) {
    if (value == null || !value.matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid amount");
    return new BigDecimal(value).setScale(2);
  }

  /** A percentage with up to two decimals: 0 to 100 for a discount limit, below 100 for a fee. */
  static BigDecimal percent(String value, boolean inclusive) {
    if (value == null || value.isBlank()) return null;
    if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid percent");
    BigDecimal percent = new BigDecimal(value).setScale(2);
    int limit = percent.compareTo(BigDecimal.valueOf(100));
    if (inclusive ? limit > 0 : limit >= 0) throw new IllegalArgumentException("Invalid percent");
    return percent;
  }

  static Integer days(Integer value) {
    if (value != null && (value < 0 || value > 120))
      throw new IllegalArgumentException("Invalid days");
    return value;
  }
}
