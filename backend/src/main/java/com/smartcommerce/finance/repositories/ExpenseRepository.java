package com.smartcommerce.finance.repositories;

import com.smartcommerce.finance.domain.Expense;
import com.smartcommerce.finance.domain.ExpenseInput;
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

/** Manual expenses (SC-020); every statement is scoped by the tenant it is given. */
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

  /** Inserts once per request id; returns whether this call created it. */
  public boolean insert(UUID tenant, UUID id, ExpenseInput input, UUID actor, Instant now) {
    return jdbc.update(
            """
            INSERT INTO expense(id,tenant_id,request_id,category,kind,description,amount,
              competence_date,paid_on,status,created_by,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)
            ON CONFLICT (tenant_id, request_id) DO NOTHING
            """,
            id,
            tenant,
            input.requestId(),
            input.category(),
            input.kind(),
            input.description(),
            input.amount(),
            Date.valueOf(input.competenceDate()),
            input.paidOn() == null ? null : Date.valueOf(input.paidOn()),
            actor,
            utc(now))
        == 1;
  }

  public Optional<Expense> byRequest(UUID tenant, UUID request) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND request_id=?",
            ExpenseRepository::expense,
            tenant,
            request)
        .stream()
        .findFirst();
  }

  public Optional<Expense> lock(UUID tenant, UUID id) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND id=? FOR UPDATE",
            ExpenseRepository::expense,
            tenant,
            id)
        .stream()
        .findFirst();
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

  /** Expenses with competence in [from, to), newest first, at most {@code limit}. */
  public List<Expense> between(UUID tenant, LocalDate from, LocalDate to, int limit) {
    return jdbc.query(
        SELECT
            + " WHERE tenant_id=? AND competence_date>=? AND competence_date<?"
            + " ORDER BY competence_date DESC, created_at DESC LIMIT ?",
        ExpenseRepository::expense,
        tenant,
        Date.valueOf(from),
        Date.valueOf(to),
        limit);
  }

  private static final String SELECT =
      """
      SELECT id,category,kind,description,amount,competence_date,paid_on,status,created_at,
        cancelled_at,cancel_reason FROM expense""";

  private static Expense expense(ResultSet row, int index) throws SQLException {
    Date paid = row.getDate("paid_on");
    return new Expense(
        row.getObject("id", UUID.class),
        row.getString("category"),
        row.getString("kind"),
        row.getString("description"),
        row.getBigDecimal("amount"),
        row.getDate("competence_date").toLocalDate(),
        paid == null ? null : paid.toLocalDate(),
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
