package com.smartcommerce.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Counter sales at the physical store (SC-053, ADR-0019 C6, C8), as stored and as read. */
public final class CounterSaleRecords {
  private CounterSaleRecords() {}

  /** A catalog variant as the counter sees it: what it is, its price and how many are available. */
  public record Product(
      UUID variantId,
      String name,
      String options,
      String sku,
      BigDecimal price,
      BigDecimal cost,
      int available) {}

  /**
   * The OWNER's settings; a store without any has no Pix account, no staff discount and unknown
   * card fees and release days.
   */
  public record Settings(
      BigDecimal staffDiscountLimit,
      UUID pixAccountId,
      BigDecimal debitFeePercent,
      Integer debitReleaseDays,
      BigDecimal creditFeePercent,
      Integer creditReleaseDays) {
    public static final Settings NONE =
        new Settings(BigDecimal.ZERO.setScale(2), null, null, null, null, null);

    public BigDecimal feePercent(String method) {
      return "DEBIT".equals(method)
          ? debitFeePercent
          : "CREDIT".equals(method) ? creditFeePercent : null;
    }

    public Integer releaseDays(String method) {
      return "DEBIT".equals(method)
          ? debitReleaseDays
          : "CREDIT".equals(method) ? creditReleaseDays : null;
    }
  }

  public record Item(
      UUID variantId,
      String name,
      String options,
      String sku,
      BigDecimal unitPrice,
      BigDecimal unitCost,
      int quantity,
      BigDecimal lineTotal) {}

  public record Sale(
      UUID id,
      UUID requestId,
      String paymentMethod,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      BigDecimal feePercent,
      BigDecimal fee,
      UUID accountId,
      String accountName,
      UUID cashSessionId,
      LocalDate expectedReleaseOn,
      String status,
      String createdBy,
      Instant createdAt,
      String cancelledBy,
      Instant cancelledAt,
      String cancelReason,
      Boolean restocked,
      List<Item> items,
      LocalDate soldOn) {
    public boolean active() {
      return "ACTIVE".equals(status);
    }

    public boolean card() {
      return "DEBIT".equals(paymentMethod) || "CREDIT".equals(paymentMethod);
    }
  }
}
