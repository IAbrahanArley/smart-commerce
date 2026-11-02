package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A validated payment of a payable (SC-051, ADR-0019 C4): the day, the account the money left,
 * interest or fine and discount (zero when absent). Only full payments: what leaves the account is
 * the amount plus the surcharge minus the discount, and it must stay positive.
 */
public record ExpensePayment(
    LocalDate paidOn, UUID accountId, BigDecimal surcharge, BigDecimal discount) {
  public static ExpensePayment of(
      String paidOn, String accountId, String surcharge, String discount, LocalDate today) {
    LocalDate day = ExpenseInput.date(paidOn);
    if (day == null || day.isBefore(ExpenseInput.FLOOR) || day.isAfter(today))
      throw new IllegalArgumentException("Invalid payment date");
    UUID account = ExpenseInput.uuid(accountId);
    if (account == null) throw new IllegalArgumentException("Invalid account");
    BigDecimal extra = optionalMoney(surcharge);
    BigDecimal off = optionalMoney(discount);
    if (extra == null || off == null) throw new IllegalArgumentException("Invalid amount");
    return new ExpensePayment(day, account, extra, off);
  }

  private static BigDecimal optionalMoney(String value) {
    return ExpenseInput.blank(value) ? BigDecimal.ZERO.setScale(2) : ExpenseInput.money(value);
  }

  /** The payment as recorded when the expense was created already paid. */
  public static ExpensePayment of(ExpenseInput input) {
    return new ExpensePayment(
        input.paidOn(),
        input.paidAccountId(),
        BigDecimal.ZERO.setScale(2),
        BigDecimal.ZERO.setScale(2));
  }

  /** Same payment as the stored one: a retry of it, not a second payment with other data. */
  public boolean sameAs(Expense stored) {
    return paidOn.equals(stored.paidOn())
        && accountId.equals(stored.paidAccountId())
        && surcharge.compareTo(stored.surcharge()) == 0
        && discount.compareTo(stored.discount()) == 0;
  }
}
