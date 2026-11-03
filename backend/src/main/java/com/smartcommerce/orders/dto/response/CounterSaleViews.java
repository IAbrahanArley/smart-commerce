package com.smartcommerce.orders.dto.response;

import com.smartcommerce.finance.services.CounterSaleMoneyService;
import com.smartcommerce.orders.domain.CounterSaleRecords;
import com.smartcommerce.orders.services.CounterSaleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Counter sale responses (SC-053). Money is text with two decimals. No response carries a cost:
 * employees see prices, availability and sales, never margins.
 */
public final class CounterSaleViews {
  private CounterSaleViews() {}

  public record Product(
      UUID variantId, String name, String options, String sku, String price, int available) {
    public static Product of(CounterSaleRecords.Product product) {
      return new Product(
          product.variantId(),
          product.name(),
          product.options(),
          product.sku(),
          money(product.price()),
          product.available());
    }
  }

  public record Item(
      UUID variantId,
      String name,
      String options,
      String sku,
      String unitPrice,
      int quantity,
      String lineTotal) {}

  public record Sale(
      UUID id,
      String paymentMethod,
      String subtotal,
      String discount,
      String total,
      String fee,
      String accountName,
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
    public static Sale of(CounterSaleRecords.Sale sale) {
      return of(sale, true);
    }

    /** Employees never see the card fee: like costs, it is margin data. */
    public static Sale of(CounterSaleRecords.Sale sale, boolean owner) {
      return new Sale(
          sale.id(),
          sale.paymentMethod(),
          money(sale.subtotal()),
          money(sale.discount()),
          money(sale.total()),
          owner ? money(sale.fee()) : null,
          sale.accountName(),
          sale.expectedReleaseOn(),
          sale.status(),
          sale.createdBy(),
          sale.createdAt(),
          sale.cancelledBy(),
          sale.cancelledAt(),
          sale.cancelReason(),
          sale.restocked(),
          sale.items().stream()
              .map(
                  item ->
                      new Item(
                          item.variantId(),
                          item.name(),
                          item.options(),
                          item.sku(),
                          money(item.unitPrice()),
                          item.quantity(),
                          money(item.lineTotal())))
              .toList(),
          sale.soldOn());
    }
  }

  public record Session(UUID id, String accountName) {}

  /**
   * The counter screen: the viewer's role, the employee discount limit, whether Pix is set up (its
   * account's name), the open cash sessions a cash sale can go into and the latest sales.
   */
  public record Overview(
      String role,
      String staffDiscountLimit,
      String pixAccountName,
      List<Session> sessions,
      List<Sale> sales) {
    public static Overview of(CounterSaleService.Overview overview) {
      return new Overview(
          overview.role(),
          money(overview.settings().staffDiscountLimit()),
          overview.pixAccountName(),
          overview.sessions().stream()
              .map(
                  (CounterSaleMoneyService.OpenSession item) ->
                      new Session(item.id(), item.accountName()))
              .toList(),
          overview.sales().stream()
              .map(sale -> Sale.of(sale, "OWNER".equals(overview.role())))
              .toList());
    }
  }

  public record Settings(
      String staffDiscountLimit,
      UUID pixAccountId,
      String debitFeePercent,
      Integer debitReleaseDays,
      String creditFeePercent,
      Integer creditReleaseDays) {
    public static Settings of(CounterSaleRecords.Settings settings) {
      return new Settings(
          money(settings.staffDiscountLimit()),
          settings.pixAccountId(),
          money(settings.debitFeePercent()),
          settings.debitReleaseDays(),
          money(settings.creditFeePercent()),
          settings.creditReleaseDays());
    }
  }

  static String money(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
