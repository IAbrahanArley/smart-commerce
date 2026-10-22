package com.smartcommerce.analytics.services;

import com.smartcommerce.analytics.dto.response.DashboardView;
import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.finance.domain.ReportPeriod;
import com.smartcommerce.finance.services.FinanceReportService;
import com.smartcommerce.inventory.domain.StockInsight;
import com.smartcommerce.inventory.services.InventoryInsightService;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store dashboard (SC-022, ADR-0015 F8). Reads only: the financial report (which checks the OWNER
 * first and fails with one 404 otherwise), the ledger's sales per day and the inventory lists. No
 * figure is computed here that the report does not already compute.
 */
@Service
public class DashboardService {
  static final int LIST_LIMIT = 10;
  private final FinanceReportService finance;
  private final InventoryInsightService inventory;

  public DashboardService(FinanceReportService finance, InventoryInsightService inventory) {
    this.finance = finance;
    this.inventory = inventory;
  }

  public LocalDate today() {
    return finance.today();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DashboardView dashboard(UUID actor, UUID tenant, ReportPeriod period) {
    var report = finance.report(actor, tenant, period);
    var days =
        finance.salesByDay(tenant, period).stream()
            .map(
                day ->
                    new DashboardView.Day(
                        day.day(),
                        day.orders(),
                        day.revenue().setScale(2, RoundingMode.UNNECESSARY).toPlainString()))
            .toList();
    var lists = inventory.lists(tenant, LIST_LIMIT);
    return new DashboardView(
        report.period(),
        report.paidOrders(),
        report.grossRevenue(),
        report.averageTicket(),
        report.contributionMargin(),
        report.marginPercent(),
        report.complete(),
        report.missing(),
        days,
        InventoryInsightService.LOW_STOCK,
        lists.lowStockCount(),
        stock(lists.lowStock()),
        (int) InventoryInsightService.STALE_AFTER.toDays(),
        lists.withoutSalesCount(),
        stock(lists.withoutSales()));
  }

  private static List<DashboardView.Stock> stock(List<StockInsight> items) {
    return items.stream()
        .map(
            item ->
                new DashboardView.Stock(
                    item.productId(),
                    item.variantId(),
                    item.productName(),
                    VariantValues.decode(item.optionsJson()),
                    item.available(),
                    item.lastSaleAt()))
        .toList();
  }
}
