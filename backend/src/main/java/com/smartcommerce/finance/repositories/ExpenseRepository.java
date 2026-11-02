package com.smartcommerce.finance.repositories;

import com.smartcommerce.finance.domain.Expense;
import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.domain.ExpensePayment;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Expenses and payables (SC-020, SC-051); every statement is scoped by the tenant it is given. */
@Repository
public class ExpenseRepository {
  private final JdbcTemplate jdbc;

  public ExpenseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** OWNER with an active, verified identity; anything else reads as absent. */
  public boolean ownsStore(UUID actor, UUID tenant) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS(SELECT 1 FROM store s
              JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=? AND m.role='OWNER'
              JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
              WHERE s.tenant_id=?)
            """,
            Boolean.class,
            actor,
            tenant));
  }

  /**
   * One row of a new expense: an installment carries its number (its group is the input's request
   * id); a monthly successor carries the expense it follows. Unpaid; a payment is {@link #pay}.
   */
  public record NewRow(
      UUID id,
      UUID request,
      BigDecimal amount,
      LocalDate competence,
      LocalDate due,
      Integer installmentNumber,
      UUID recurrenceSource) {}

  /** Inserts once per request id; returns whether this call created it. */
  public boolean insert(UUID tenant, ExpenseInput input, NewRow row, UUID actor, Instant now) {
    boolean installment = row.installmentNumber() != null;
    return jdbc.update(
            """
            INSERT INTO expense(id,tenant_id,request_id,category,kind,description,amount,
              competence_date,due_date,supplier,document_number,installment_group,
              installment_number,installment_count,recurrence,recurrence_source_id,status,
              created_by,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)
            ON CONFLICT (tenant_id, request_id) DO NOTHING
            """,
            row.id(),
            tenant,
            row.request(),
            input.category(),
            input.kind(),
            input.description(),
            row.amount(),
            Date.valueOf(row.competence()),
            Date.valueOf(row.due()),
            input.supplier(),
            input.documentNumber(),
            installment ? input.requestId() : null,
            row.installmentNumber(),
            installment ? input.installments() : null,
            input.recurrence(),
            row.recurrenceSource(),
            actor,
            utc(now))
        == 1;
  }

  /** Records the payment of an active, unpaid expense; returns whether this call did it. */
  public boolean pay(UUID tenant, UUID id, ExpensePayment payment, UUID actor, Instant now) {
    return jdbc.update(
            """
            UPDATE expense SET paid_on=?, paid_account_id=?, surcharge_amount=?,
              discount_amount=?, paid_by=?, paid_at=?
            WHERE tenant_id=? AND id=? AND status='ACTIVE' AND paid_on IS NULL
            """,
            Date.valueOf(payment.paidOn()),
            payment.accountId(),
            payment.surcharge(),
            payment.discount(),
            actor,
            utc(now),
            tenant,
            id)
        == 1;
  }

  public Optional<Expense> byRequest(UUID tenant, UUID request) {
    return one(" WHERE x.tenant_id=? AND x.request_id=?", tenant, request);
  }

  public Optional<Expense> byId(UUID tenant, UUID id) {
    return one(" WHERE x.tenant_id=? AND x.id=?", tenant, id);
  }

  /** The next month's bill a paid monthly one created, locked; none before it was paid. */
  public Optional<Expense> lockSuccessor(UUID tenant, UUID source) {
    return one(" WHERE x.tenant_id=? AND x.recurrence_source_id=? FOR UPDATE OF x", tenant, source);
  }

  public Optional<Expense> lock(UUID tenant, UUID id) {
    return one(" WHERE x.tenant_id=? AND x.id=? FOR UPDATE OF x", tenant, id);
  }

  public boolean cancel(UUID tenant, UUID id, UUID actor, String reason, Instant now) {
    return jdbc.update(
            """
            UPDATE expense SET status='CANCELLED', cancelled_by=?, cancelled_at=?, cancel_reason=?
            WHERE tenant_id=? AND id=? AND status='ACTIVE'
            """,
            actor,
            utc(now),
            reason,
            tenant,
            id)
        == 1;
  }

  /** Expenses falling due in [from, to), soonest first, at most {@code limit}. */
  public List<Expense> dueBetween(UUID tenant, LocalDate from, LocalDate to, int limit) {
    return jdbc.query(
        SELECT
            + " WHERE x.tenant_id=? AND x.due_date>=? AND x.due_date<?"
            + " ORDER BY x.due_date, x.created_at, x.id LIMIT ?",
        ExpenseRepository::expense,
        tenant,
        Date.valueOf(from),
        Date.valueOf(to),
        limit);
  }

  /** Active, unpaid expenses due before {@code today}, oldest due first, at most {@code limit}. */
  public List<Expense> overdue(UUID tenant, LocalDate today, int limit) {
    return jdbc.query(
        SELECT
            + " WHERE x.tenant_id=? AND x.status='ACTIVE' AND x.paid_on IS NULL AND x.due_date<?"
            + " ORDER BY x.due_date, x.created_at, x.id LIMIT ?",
        ExpenseRepository::expense,
        tenant,
        Date.valueOf(today),
        limit);
  }

  public record MonthTotals(BigDecimal toPay, BigDecimal paid) {}

  /**
   * Over every active bill due in [from, to), never a truncated list: still to pay (amounts) and
   * paid (what left the accounts: amount plus interest or fine minus discount).
   */
  public MonthTotals monthTotals(UUID tenant, LocalDate from, LocalDate to) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(amount) FILTER (WHERE paid_on IS NULL),0),
          COALESCE(SUM(amount+surcharge_amount-discount_amount) FILTER (WHERE paid_on IS NOT NULL),0)
        FROM expense WHERE tenant_id=? AND status='ACTIVE' AND due_date>=? AND due_date<?
        """,
        (row, index) ->
            new MonthTotals(row.getBigDecimal(1).setScale(2), row.getBigDecimal(2).setScale(2)),
        tenant,
        Date.valueOf(from),
        Date.valueOf(to));
  }

  public record Overdue(long count, BigDecimal total) {}

  /** How many active, unpaid expenses are past due, and how much they add up to. */
  public Overdue overdueTotals(UUID tenant, LocalDate today) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*), COALESCE(SUM(amount),0) FROM expense
        WHERE tenant_id=? AND status='ACTIVE' AND paid_on IS NULL AND due_date<?
        """,
        (row, index) -> new Overdue(row.getLong(1), row.getBigDecimal(2).setScale(2)),
        tenant,
        Date.valueOf(today));
  }

  private Optional<Expense> one(String where, Object... arguments) {
    return jdbc.query(SELECT + where, ExpenseRepository::expense, arguments).stream().findFirst();
  }

  private static final String SELECT =
      """
      SELECT x.id,x.category,x.kind,x.description,x.amount,x.competence_date,x.due_date,
        x.supplier,x.document_number,x.installment_number,x.installment_count,x.recurrence,
        x.paid_on,x.paid_account_id,a.name AS paid_account_name,x.surcharge_amount,
        x.discount_amount,x.status,x.created_at,x.cancelled_at,x.cancel_reason
      FROM expense x
      LEFT JOIN finance_account a ON a.id=x.paid_account_id AND a.tenant_id=x.tenant_id""";

  private static Expense expense(ResultSet row, int index) throws SQLException {
    Date paid = row.getDate("paid_on");
    return new Expense(
        row.getObject("id", UUID.class),
        row.getString("category"),
        row.getString("kind"),
        row.getString("description"),
        row.getBigDecimal("amount"),
        row.getDate("competence_date").toLocalDate(),
        row.getDate("due_date").toLocalDate(),
        row.getString("supplier"),
        row.getString("document_number"),
        row.getObject("installment_number", Integer.class),
        row.getObject("installment_count", Integer.class),
        row.getString("recurrence"),
        paid == null ? null : paid.toLocalDate(),
        row.getObject("paid_account_id", UUID.class),
        row.getString("paid_account_name"),
        row.getBigDecimal("surcharge_amount").setScale(2),
        row.getBigDecimal("discount_amount").setScale(2),
        row.getString("status"),
        instant(row, "created_at"),
        instant(row, "cancelled_at"),
        row.getString("cancel_reason"));
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
