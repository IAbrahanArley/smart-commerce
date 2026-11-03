package com.smartcommerce.finance.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * The store's financial summary of a period (SC-021, ADR-0015 F1–F5). Money is text with two
 * decimals; deductions (refunds, fees, COGS, expenses) are shown as positive amounts and the margin
 * keeps its sign. {@code complete} is false whenever a component is unknown, and {@code missing}
 * says what: then the margin is a partial figure, never a definitive profit. {@code
 * cashDifferences} (SC-052) is the drawer counts minus the expected amounts, signed (negative is
 * cash missing): a result line of its own, outside the contribution margin.
 */
public record FinanceReportView(
    Period period,
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
    List<Missing> missing,
    long paidOrders,
    String averageTicket,
    Receivable receivable,
    String cashMovement,
    String cashDifferences) {
  /** {@code lastDay} is inclusive. */
  public record Period(String key, LocalDate firstDay, LocalDate lastDay) {}

  /** FEES_UNAVAILABLE (sales without the provider fee) or COST_MISSING (orders with no cost). */
  public record Missing(String code, long count) {}

  /**
   * Sales not yet released by the provider, after refunds and known fees; {@code feesUnknown}
   * counts sales whose fee is still unknown, so the amount is before those fees.
   */
  public record Receivable(String amount, long feesUnknown) {}
}
