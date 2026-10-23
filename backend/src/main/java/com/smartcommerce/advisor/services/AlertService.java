package com.smartcommerce.advisor.services;

import com.smartcommerce.advisor.dto.response.AlertsView;
import com.smartcommerce.finance.domain.ReportPeriod;
import com.smartcommerce.finance.services.FinanceReportService;
import com.smartcommerce.inventory.services.InventoryInsightService;
import com.smartcommerce.orders.services.OrderAdminService;
import com.smartcommerce.payments.services.PaymentConnectionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store alerts (SC-024, ADR-0016 A6). Pure rules over services that already compute and isolate the
 * figures: the financial report of the current month (which also checks the OWNER and fails with
 * one 404 otherwise), the inventory lists, pending orders near their deadline and the Mercado Pago
 * connection. Nothing here depends on AI, and nothing is estimated.
 */
@Service
public class AlertService {
  /** Pending orders whose reservation ends within this window raise ORDERS_EXPIRING. */
  public static final Duration EXPIRING_WITHIN = Duration.ofMinutes(10);

  private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "ATTENTION", "INFO");
  private final FinanceReportService finance;
  private final InventoryInsightService inventory;
  private final OrderAdminService orders;
  private final PaymentConnectionService payments;
  private final Clock clock;

  public AlertService(
      FinanceReportService finance,
      InventoryInsightService inventory,
      OrderAdminService orders,
      PaymentConnectionService payments,
      Clock clock) {
    this.finance = finance;
    this.inventory = inventory;
    this.orders = orders;
    this.payments = payments;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AlertsView alerts(UUID actor, UUID tenant) {
    ReportPeriod month = ReportPeriod.of("THIS_MONTH", null, finance.today());
    var report = finance.report(actor, tenant, month);
    List<AlertsView.Alert> alerts = new ArrayList<>();

    String status = payments.status(actor, tenant).status();
    if ("EXPIRED".equals(status) || "REVOKED".equals(status))
      alerts.add(alert("PAYMENTS_" + status, "CRITICAL", null, null, "PAYMENTS"));
    else if ("NOT_CONNECTED".equals(status))
      alerts.add(alert("PAYMENTS_NOT_CONNECTED", "ATTENTION", null, null, "PAYMENTS"));

    if (report.complete()
        && report.contributionMargin() != null
        && new BigDecimal(report.contributionMargin()).signum() < 0)
      alerts.add(
          alert("MARGIN_NEGATIVE", "CRITICAL", null, report.contributionMargin(), "FINANCE"));
    for (var missing : report.missing()) {
      if ("COST_MISSING".equals(missing.code()))
        alerts.add(alert("COST_MISSING", "ATTENTION", missing.count(), null, "FINANCE"));
      else if ("FEES_UNAVAILABLE".equals(missing.code()))
        alerts.add(alert("FEES_UNAVAILABLE", "INFO", missing.count(), null, "FINANCE"));
    }

    long expiring = orders.expiringSoon(actor, tenant, EXPIRING_WITHIN);
    if (expiring > 0) alerts.add(alert("ORDERS_EXPIRING", "ATTENTION", expiring, null, "ORDERS"));

    var stock = inventory.lists(tenant, 0);
    if (stock.lowStockCount() > 0)
      alerts.add(alert("LOW_STOCK", "ATTENTION", stock.lowStockCount(), null, "PRODUCTS"));
    if (stock.withoutSalesCount() > 0)
      alerts.add(alert("WITHOUT_SALES", "INFO", stock.withoutSalesCount(), null, "PRODUCTS"));

    alerts.sort(Comparator.comparingInt(alert -> SEVERITY_ORDER.indexOf(alert.severity())));
    return new AlertsView(clock.instant(), month.key(), List.copyOf(alerts));
  }

  private static AlertsView.Alert alert(
      String code, String severity, Long count, String amount, String target) {
    return new AlertsView.Alert(code, severity, count, amount, target);
  }
}
