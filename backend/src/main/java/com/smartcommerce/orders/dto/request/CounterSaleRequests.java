package com.smartcommerce.orders.dto.request;

import java.util.List;

/** Counter sale bodies (SC-053), validated by {@code CounterSaleInput}; money as decimal text. */
public final class CounterSaleRequests {
  private CounterSaleRequests() {}

  public record Line(String variantId, Integer quantity) {}

  /** {@code paymentMethod}: CASH (with {@code cashSessionId}), PIX, DEBIT or CREDIT. */
  public record Sale(
      String requestId,
      List<Line> items,
      String discount,
      String paymentMethod,
      String cashSessionId) {}

  public record Cancel(String reason, Boolean restock) {}

  /** Percentages as decimal text; blank fees and days stay unknown. */
  public record Settings(
      String staffDiscountLimit,
      String pixAccountId,
      String debitFeePercent,
      Integer debitReleaseDays,
      String creditFeePercent,
      Integer creditReleaseDays) {}
}
