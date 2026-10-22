package com.smartcommerce.analytics.dto.response;

import com.smartcommerce.catalog.domain.VariantOption;
import com.smartcommerce.finance.dto.response.FinanceReportView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The store dashboard (SC-022, ADR-0015 F8): the period's figures are the financial report's own
 * (same ledger, same numbers), plus sales per day and the stock lists. Lists are capped; the counts
 * are the totals.
 */
public record DashboardView(
    FinanceReportView.Period period,
    long paidOrders,
    String revenue,
    String averageTicket,
    String contributionMargin,
    String marginPercent,
    boolean marginComplete,
    List<FinanceReportView.Missing> missing,
    List<Day> salesByDay,
    int lowStockThreshold,
    long lowStockCount,
    List<Stock> lowStock,
    int staleAfterDays,
    long withoutSalesCount,
    List<Stock> withoutSales) {
  public record Day(LocalDate day, long orders, String revenue) {}

  public record Stock(
      UUID productId,
      UUID variantId,
      String productName,
      List<VariantOption> options,
      int available,
      Instant lastSaleAt) {}
}
