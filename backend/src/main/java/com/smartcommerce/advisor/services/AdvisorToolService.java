package com.smartcommerce.advisor.services;

import com.smartcommerce.advisor.domain.AdvisorException;
import com.smartcommerce.advisor.domain.AdvisorTool;
import com.smartcommerce.advisor.dto.tool.ToolResults;
import com.smartcommerce.advisor.repositories.AdvisorUsageRepository;
import com.smartcommerce.analytics.services.DashboardService;
import com.smartcommerce.catalog.domain.VariantOption;
import com.smartcommerce.catalog.domain.VariantValues;
import com.smartcommerce.finance.domain.ReportPeriod;
import com.smartcommerce.finance.dto.response.FinanceReportView;
import com.smartcommerce.finance.services.FinanceReportService;
import com.smartcommerce.inventory.domain.StockInsight;
import com.smartcommerce.inventory.services.InventoryInsightService;
import com.smartcommerce.orders.services.OrderAdminService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the assistant's read-only tools (SC-025, ADR-0016 A3, A4). The store and the actor always
 * come from the caller (the session), never from the arguments, which are validated strictly
 * against each tool's closed lists. Every figure comes from a service that already computes and
 * isolates it; results are aggregates and catalog only.
 */
@Service
public class AdvisorToolService {
  private final AdvisorUsageRepository usage;
  private final FinanceReportService finance;
  private final DashboardService dashboard;
  private final AlertService alerts;
  private final InventoryInsightService inventory;
  private final OrderAdminService orders;

  public AdvisorToolService(
      AdvisorUsageRepository usage,
      FinanceReportService finance,
      DashboardService dashboard,
      AlertService alerts,
      InventoryInsightService inventory,
      OrderAdminService orders) {
    this.usage = usage;
    this.finance = finance;
    this.dashboard = dashboard;
    this.alerts = alerts;
    this.inventory = inventory;
    this.orders = orders;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Object execute(UUID actor, UUID tenant, String toolName, Map<String, Object> arguments) {
    AdvisorTool tool = AdvisorTool.named(toolName);
    Map<String, Object> args = validated(tool, arguments == null ? Map.of() : arguments);
    if (!usage.ownsStore(actor, tenant))
      throw new AdvisorException(AdvisorException.Reason.MISSING);
    return switch (tool) {
      case RELATORIO_PERIODO -> report(actor, tenant, period(args));
      case INDICADORES_DASHBOARD -> dashboard(actor, tenant, period(args));
      case ALERTAS -> alerts.alerts(actor, tenant);
      case ESTOQUE_PRODUTOS ->
          stock(
              tenant,
              (String) args.get("filtro"),
              (Integer) args.getOrDefault("limite", AdvisorTool.DEFAULT_LIMIT));
      case RESUMO_PEDIDOS -> orders(actor, tenant, period(args));
    };
  }

  /** Only the tool's arguments, each from its closed list; anything else is rejected. */
  static Map<String, Object> validated(AdvisorTool tool, Map<String, Object> arguments) {
    for (String name : arguments.keySet()) if (!tool.args().containsKey(name)) throw invalid();
    for (var entry : tool.args().entrySet()) {
      Object value = arguments.get(entry.getKey());
      if (value == null) {
        if (entry.getValue().required()) throw invalid();
        continue;
      }
      if (entry.getValue() == AdvisorTool.Arg.LIMIT) {
        if (!(value instanceof Integer number) || number < 1 || number > AdvisorTool.MAX_LIMIT)
          throw invalid();
      } else if (!(value instanceof String text) || !entry.getValue().allowed().contains(text))
        throw invalid();
    }
    return arguments;
  }

  private ReportPeriod period(Map<String, Object> args) {
    return ReportPeriod.of((String) args.get("periodo"), null, finance.today());
  }

  private ToolResults.Report report(UUID actor, UUID tenant, ReportPeriod period) {
    var view = finance.report(actor, tenant, period);
    return new ToolResults.Report(
        view.period(),
        view.grossRevenue(),
        view.refunds(),
        view.fees(),
        view.netRevenue(),
        view.cogs(),
        view.variableExpenses(),
        view.contributionMargin(),
        view.marginPercent(),
        view.fixedExpenses(),
        view.complete(),
        view.missing(),
        view.paidOrders(),
        view.averageTicket(),
        view.receivable() == null ? null : view.receivable().amount(),
        view.receivable() == null ? 0 : view.receivable().feesUnknown(),
        view.cashMovement());
  }

  private ToolResults.Dashboard dashboard(UUID actor, UUID tenant, ReportPeriod period) {
    var view = dashboard.dashboard(actor, tenant, period);
    return new ToolResults.Dashboard(
        view.period(),
        view.paidOrders(),
        view.revenue(),
        view.averageTicket(),
        view.contributionMargin(),
        view.marginPercent(),
        view.marginComplete(),
        view.missing(),
        view.salesByDay().stream()
            .map(day -> new ToolResults.Day(day.day(), day.orders(), day.revenue()))
            .toList(),
        view.lowStockThreshold(),
        view.lowStockCount(),
        view.staleAfterDays(),
        view.withoutSalesCount());
  }

  private ToolResults.Stock stock(UUID tenant, String filter, int limit) {
    var lists = inventory.lists(tenant, limit);
    boolean low = "ESTOQUE_BAIXO".equals(filter);
    List<StockInsight> items = low ? lists.lowStock() : lists.withoutSales();
    return new ToolResults.Stock(
        filter,
        low ? lists.lowStockCount() : lists.withoutSalesCount(),
        items.stream()
            .map(
                item ->
                    new ToolResults.StockItem(
                        item.productName(),
                        options(VariantValues.decode(item.optionsJson())),
                        item.available(),
                        item.lastSaleAt()))
            .toList());
  }

  private ToolResults.Orders orders(UUID actor, UUID tenant, ReportPeriod period) {
    Map<String, Long> counts = orders.countsByStatus(actor, tenant, period.from(), period.to());
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    return new ToolResults.Orders(
        new FinanceReportView.Period(period.key(), period.firstDay(), period.endDay().minusDays(1)),
        counts,
        total);
  }

  private static String options(List<VariantOption> options) {
    return options.stream()
        .map(option -> option.name() + ": " + option.value())
        .collect(Collectors.joining(", "));
  }

  private static AdvisorException invalid() {
    return new AdvisorException(AdvisorException.Reason.INVALID_TOOL_CALL);
  }
}
