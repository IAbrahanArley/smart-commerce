package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Validated cash register requests (SC-052, ADR-0019 C5). Money is text with up to two decimals,
 * never negative; reasons are one line of 3 to 200 characters.
 */
public final class CashInput {
  private CashInput() {}

  /** Opening a drawer with the counted change float (zero allowed). */
  public record Open(UUID requestId, UUID accountId, BigDecimal amount) {
    public static Open of(String requestId, String accountId, String amount) {
      return new Open(id(requestId), id(accountId), count(amount));
    }
  }

  /**
   * DROP takes cash out of the drawer, TOP_UP puts cash in: to or from another account of the store
   * (a transfer) or, without one, as the owner's withdrawal or contribution. EXPENSE pays an
   * expense with the drawer's cash, in one of the expense categories.
   */
  public record Movement(
      UUID requestId,
      String type,
      BigDecimal amount,
      String reason,
      UUID accountId,
      String category) {
    public static Movement of(
        String requestId,
        String type,
        String amount,
        String reason,
        String accountId,
        String category) {
      if (!"DROP".equals(type) && !"TOP_UP".equals(type) && !"EXPENSE".equals(type))
        throw new IllegalArgumentException("Invalid type");
      BigDecimal value = count(amount);
      if (value.signum() <= 0) throw new IllegalArgumentException("Invalid amount");
      UUID other = ExpenseInput.blank(accountId) ? null : id(accountId);
      boolean expense = "EXPENSE".equals(type);
      if (expense && (other != null || !ExpenseInput.listed(ExpenseInput.CATEGORIES, category)))
        throw new IllegalArgumentException("Invalid expense");
      if (!expense && !ExpenseInput.blank(category))
        throw new IllegalArgumentException("Invalid category");
      return new Movement(
          id(requestId), type, value, ExpenseInput.text(reason), other, expense ? category : null);
    }
  }

  /** Closing with the blind count. */
  public record Close(UUID requestId, BigDecimal counted) {
    public static Close of(String requestId, String counted) {
      return new Close(id(requestId), count(counted));
    }
  }

  private static UUID id(String value) {
    UUID id = ExpenseInput.uuid(value);
    if (id == null) throw new IllegalArgumentException("Invalid id");
    return id;
  }

  private static BigDecimal count(String value) {
    BigDecimal money = ExpenseInput.money(value);
    if (money == null) throw new IllegalArgumentException("Invalid amount");
    return money;
  }
}
