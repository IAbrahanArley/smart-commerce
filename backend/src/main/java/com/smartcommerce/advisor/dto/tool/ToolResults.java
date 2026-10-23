package com.smartcommerce.advisor.dto.tool;

import com.smartcommerce.finance.dto.response.FinanceReportView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What the assistant's tools return (SC-025, ADR-0016 A3, A4). Aggregates and catalog only: no
 * buyer, order, payment or user identifier and no cost per item. Money is text with two decimals,
 * exactly as the reports show it; {@code complete=false} plus {@code missing} means a partial
 * figure that must be reported as such.
 */
public final class ToolResults {
  private ToolResults() {}

  public record Report(
      FinanceReportView.Period period,
      String grossRevenue,
      String refunds,
      String fees,
      String netRevenue,
      String cogs,
      String variableExpenses,
      String contributionMargin,
      String marginPercent,
      String fixedExpenses,
      boolean complete,
      List<FinanceReportView.Missing> missing,
      long paidOrders,
      String averageTicket,
      String receivable,
      long receivableFeesUnknown,
      String cashMovement) {}

  public record Dashboard(
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
      int staleAfterDays,
      long withoutSalesCount) {}

  public record Day(LocalDate day, long orders, String revenue) {}

  public record Stock(String filter, long total, List<StockItem> items) {}

  /** {@code options} like "Cor: Azul, Tamanho: M"; {@code lastSaleAt} null when never sold. */
  public record StockItem(String productName, String options, int available, Instant lastSaleAt) {}

  /** Orders created in the period by status (pending past the deadline counts as EXPIRED). */
  public record Orders(FinanceReportView.Period period, Map<String, Long> byStatus, long total) {}
}
