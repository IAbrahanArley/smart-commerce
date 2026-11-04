package com.smartcommerce.finance.dto.response;

import com.smartcommerce.customers.domain.StoreCustomer;
import com.smartcommerce.customers.dto.response.CustomerViews;
import com.smartcommerce.finance.domain.Credit;
import com.smartcommerce.finance.services.CreditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Store credit responses (SC-055). Money is text with two decimals. An employee gets the customer
 * being served with the phone masked, what is open and where the money can go (names only, never a
 * balance); the limit, the history and the store's list are the OWNER's.
 */
public final class CreditViews {
  private CreditViews() {}

  public record Installment(
      UUID id,
      UUID saleId,
      Instant soldAt,
      int number,
      int count,
      LocalDate dueOn,
      String amount,
      String received,
      String open,
      boolean overdue) {
    static Installment of(Credit.Installment installment, LocalDate today) {
      return new Installment(
          installment.id(),
          installment.saleId(),
          installment.soldAt(),
          installment.number(),
          installment.count(),
          installment.dueOn(),
          money(installment.amount()),
          money(installment.received()),
          money(installment.open()),
          installment.overdue(today));
    }
  }

  public record Event(
      String kind,
      UUID id,
      Instant at,
      String amount,
      Integer installmentCount,
      String status,
      String accountName,
      String by) {
    static Event of(Credit.Event event) {
      return new Event(
          event.kind(),
          event.id(),
          event.at(),
          money(event.amount()),
          event.installmentCount(),
          event.status(),
          event.accountName(),
          event.by());
    }
  }

  public record Destination(UUID id, String name) {}

  public record Customer(
      CustomerViews.Customer customer,
      String open,
      String overdue,
      String availableCredit,
      List<Installment> installments,
      List<Event> history,
      String nextCursor,
      List<Destination> sessions,
      List<Destination> banks) {
    public static Customer of(CreditService.CustomerView view) {
      StoreCustomer customer = view.customer();
      BigDecimal limit = customer.creditLimit();
      return new Customer(
          CustomerViews.Customer.of(customer, view.owner()),
          money(view.open()),
          money(view.overdue()),
          view.owner() && limit != null
              ? money(limit.subtract(view.open()).max(BigDecimal.ZERO))
              : null,
          view.installments().stream().map(item -> Installment.of(item, view.today())).toList(),
          view.history().stream().map(Event::of).toList(),
          view.nextCursor() == null ? null : view.nextCursor().text(),
          view.destinations().sessions().stream()
              .map(session -> new Destination(session.id(), session.accountName()))
              .toList(),
          view.destinations().banks().stream()
              .map(bank -> new Destination(bank.id(), bank.name()))
              .toList());
    }
  }

  public record CustomerLine(
      UUID id,
      String name,
      String phone,
      String creditLimit,
      String open,
      String overdue,
      LocalDate nextDueOn) {}

  public record Overview(
      String open,
      String overdue,
      long overdueCustomers,
      List<CustomerLine> customers,
      String nextCursor) {
    public static Overview of(CreditService.Overview overview) {
      return new Overview(
          money(overview.totals().open()),
          money(overview.totals().overdue()),
          overview.totals().overdueCustomers(),
          overview.customers().stream()
              .map(
                  line ->
                      new CustomerLine(
                          line.id(),
                          line.name(),
                          line.phone(),
                          money(line.creditLimit()),
                          money(line.open()),
                          money(line.overdue()),
                          line.nextDueOn()))
              .toList(),
          overview.nextCursor() == null ? null : overview.nextCursor().text());
    }
  }

  static String money(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
