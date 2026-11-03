package com.smartcommerce.orders.dto.response;

import com.smartcommerce.orders.services.CounterImportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spreadsheet import responses (SC-054). Money is text with two decimals. Nothing of the file is
 * echoed beyond what was read as sale data (dates, codes, SKUs, quantities, prices).
 */
public final class CounterImportViews {
  private CounterImportViews() {}

  public record Item(
      int line, String sku, String name, int quantity, String unitPrice, String discount) {}

  public record Sale(
      List<Integer> lines,
      LocalDate date,
      String code,
      String paymentMethod,
      List<Item> items,
      String total,
      String status,
      List<String> messages) {}

  public record Problem(int line, String message) {}

  public record Preview(
      int itemLines,
      long ready,
      long alreadyImported,
      long withErrors,
      String readyTotal,
      boolean needsCashAccount,
      boolean fileRefused,
      List<Problem> problems,
      List<Sale> sales) {
    public static Preview of(CounterImportService.Preview preview) {
      return new Preview(
          preview.itemLines(),
          preview.count(CounterImportService.Status.READY),
          preview.count(CounterImportService.Status.IMPORTED),
          preview.count(CounterImportService.Status.ERROR),
          money(
              preview.sales().stream()
                  .filter(sale -> sale.status() == CounterImportService.Status.READY)
                  .map(CounterImportService.PreviewSale::total)
                  .reduce(BigDecimal.ZERO, BigDecimal::add)),
          preview.needsCashAccount(),
          preview.fileRefused(),
          preview.problems().stream()
              .map(problem -> new Problem(problem.line(), problem.message()))
              .toList(),
          preview.sales().stream()
              .map(
                  sale ->
                      new Sale(
                          sale.lines(),
                          sale.date(),
                          sale.code(),
                          sale.method(),
                          sale.items().stream()
                              .map(
                                  item ->
                                      new Item(
                                          item.line(),
                                          item.sku(),
                                          item.name(),
                                          item.quantity(),
                                          money(item.unitPrice()),
                                          money(item.discount())))
                              .toList(),
                          money(sale.total()),
                          sale.status().name(),
                          sale.messages()))
              .toList());
    }
  }

  public record Result(UUID importId, int imported, Preview preview) {
    public static Result of(CounterImportService.Result result) {
      return new Result(result.importId(), result.imported(), Preview.of(result.preview()));
    }
  }

  static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
