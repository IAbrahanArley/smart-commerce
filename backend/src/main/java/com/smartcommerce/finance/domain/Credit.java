package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Store credit ("crediário", SC-055, ADR-0019 C9): installments of credit sales and the customer's
 * receipts, as stored and as read. A customer's balance is the sum of the installments of their
 * active credit sales minus what was received.
 */
public final class Credit {
  public static final int MAX_INSTALLMENTS = 12;

  private Credit() {}

  /**
   * The amounts of {@code count} installments of {@code total}: equal cents, the last one absorbing
   * the rest. The amounts are all positive when {@code total} has at least {@code count} cents.
   */
  public static List<BigDecimal> split(BigDecimal total, int count) {
    if (count < 1 || count > MAX_INSTALLMENTS) throw new IllegalArgumentException("Invalid count");
    BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN).setScale(2);
    if (each.signum() <= 0) throw new IllegalArgumentException("Invalid installments");
    List<BigDecimal> amounts = new ArrayList<>();
    for (int index = 1; index < count; index++) amounts.add(each);
    amounts.add(total.subtract(each.multiply(BigDecimal.valueOf(count - 1))));
    return amounts;
  }

  /** An installment: what it is worth, what was received of it and what is left. */
  public record Installment(
      UUID id,
      UUID saleId,
      Instant soldAt,
      int number,
      int count,
      LocalDate dueOn,
      BigDecimal amount,
      BigDecimal received) {
    public BigDecimal open() {
      return amount.subtract(received);
    }

    public boolean paid() {
      return open().signum() == 0;
    }

    /** Open on the day after it is due, in São Paulo. */
    public boolean overdue(LocalDate today) {
      return !paid() && dueOn.isBefore(today);
    }
  }

  /** A receipt as the customer's history shows it. */
  public record Receipt(
      UUID id,
      UUID requestId,
      UUID customerId,
      BigDecimal amount,
      UUID accountId,
      UUID cashSessionId,
      LocalDate receivedOn,
      Instant createdAt) {}

  /** One line of the customer's history: a credit sale or a receipt, newest first. */
  public record Event(
      String kind,
      UUID id,
      Instant at,
      BigDecimal amount,
      Integer installmentCount,
      String status,
      String accountName,
      String by) {}

  /** A customer in the OWNER's list, with what is open and overdue. */
  public record CustomerLine(
      UUID id,
      String name,
      String phone,
      BigDecimal creditLimit,
      Instant createdAt,
      BigDecimal open,
      BigDecimal overdue,
      LocalDate nextDueOn) {}

  /** The store's totals: open, overdue and how many customers have something overdue. */
  public record Totals(BigDecimal open, BigDecimal overdue, long overdueCustomers) {}
}
