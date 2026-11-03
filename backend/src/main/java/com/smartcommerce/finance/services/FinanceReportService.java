package com.smartcommerce.finance.services;

import com.smartcommerce.finance.domain.FinanceException;
import com.smartcommerce.finance.domain.ReportPeriod;
import com.smartcommerce.finance.dto.response.FinanceReportView;
import com.smartcommerce.finance.repositories.ExpenseRepository;
import com.smartcommerce.finance.repositories.FinanceReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Financial report of a period (SC-021, ADR-0015 F1–F5, F10), computed only from the ledger in one
 * consistent snapshot.
 *
 * <ul>
 *   <li>Competence (F1): sales and their COGS at payment, refunds and reversals when confirmed,
 *       expenses on their date; all in the São Paulo calendar.
 *   <li>Contribution margin (F5) = gross revenue − refunds − fees − COGS − variable expenses. It is
 *       marked incomplete, with the reasons, when any sale lacks its provider fee or any order
 *       lacks a cost; it is never presented as a definitive profit.
 *   <li>Receivable and cash (F3): sales not yet released are receivable (before unknown fees); cash
 *       is what moved in the period by settlement date.
 * </ul>
 */
@Service
public class FinanceReportService {
  private final FinanceReportRepository reports;
  private final ExpenseRepository expenses;
  private final Clock clock;

  public FinanceReportService(
      FinanceReportRepository reports, ExpenseRepository expenses, Clock clock) {
    this.reports = reports;
    this.expenses = expenses;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(ReportPeriod.ZONE));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FinanceReportView report(UUID actor, UUID tenant, ReportPeriod period) {
    if (!expenses.ownsStore(actor, tenant))
      throw new FinanceException(FinanceException.Reason.MISSING);
    var totals = reports.totals(tenant, period.from(), period.to());
    BigDecimal gross = totals.sales();
    BigDecimal refunds = totals.refunds().negate();
    BigDecimal fees = totals.fees().negate();
    BigDecimal cogs = totals.cogs().negate();
    BigDecimal variable = totals.variableExpenses().negate();
    BigDecimal fixed = totals.fixedExpenses().negate();
    BigDecimal net = gross.subtract(refunds).subtract(fees);
    BigDecimal margin = net.subtract(cogs).subtract(variable);
    List<FinanceReportView.Missing> missing = new ArrayList<>();
    if (totals.salesWithoutFee() > 0)
      missing.add(new FinanceReportView.Missing("FEES_UNAVAILABLE", totals.salesWithoutFee()));
    if (totals.incompleteCosts() > 0)
      missing.add(new FinanceReportView.Missing("COST_MISSING", totals.incompleteCosts()));
    BigDecimal base = gross.subtract(refunds);
    var receivable =
        reports.receivable(
            tenant, period.to().isAfter(clock.instant()) ? clock.instant() : period.to());
    return new FinanceReportView(
        new FinanceReportView.Period(period.key(), period.firstDay(), period.endDay().minusDays(1)),
        money(gross),
        money(refunds),
        money(fees),
        money(net),
        money(cogs),
        money(variable),
        money(margin),
        base.signum() > 0
            ? margin
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_EVEN)
                .toPlainString()
            : null,
        money(fixed),
        missing.isEmpty(),
        missing,
        totals.salesCount(),
        totals.salesCount() > 0
            ? money(
                gross.divide(BigDecimal.valueOf(totals.salesCount()), 2, RoundingMode.HALF_EVEN))
            : null,
        new FinanceReportView.Receivable(money(receivable.amount()), receivable.salesWithoutFee()),
        money(reports.settled(tenant, period.from(), period.to())),
        money(totals.cashDifferences()),
        money(totals.informedFees().negate()));
  }

  /**
   * Sales per day of the period, every day present (zero when nothing sold), for the dashboard
   * chart (SC-022). Internal: the caller has already checked the OWNER through {@link #report}.
   */
  @Transactional(readOnly = true)
  public List<FinanceReportRepository.DaySales> salesByDay(UUID tenant, ReportPeriod period) {
    Map<LocalDate, FinanceReportRepository.DaySales> found = new HashMap<>();
    for (var day : reports.salesByDay(tenant, period.from(), period.to()))
      found.put(day.day(), day);
    List<FinanceReportRepository.DaySales> days = new ArrayList<>();
    for (LocalDate day = period.firstDay(); day.isBefore(period.endDay()); day = day.plusDays(1))
      days.add(
          found.getOrDefault(day, new FinanceReportRepository.DaySales(day, 0, BigDecimal.ZERO)));
    return days;
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
