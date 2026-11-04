package com.smartcommerce.orders.dto.request;

import java.util.List;

/** Counter sale bodies (SC-053), validated by {@code CounterSaleInput}; money as decimal text. */
public final class CounterSaleRequests {
  private CounterSaleRequests() {}

  public record Line(String variantId, Integer quantity) {}

  /**
   * {@code paymentMethod}: CASH (with {@code cashSessionId}), PIX, DEBIT, CREDIT or STORE_CREDIT
   * (with {@code customerId}, {@code installments} from 1 to 12 and {@code firstDueOn},
   * yyyy-mm-dd).
   */
  public record Sale(
      String requestId,
      List<Line> items,
      String discount,
      String paymentMethod,
      String cashSessionId,
      String customerId,
      Integer installments,
      String firstDueOn) {}

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
