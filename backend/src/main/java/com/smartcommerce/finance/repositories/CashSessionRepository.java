package com.smartcommerce.finance.repositories;

import com.smartcommerce.finance.domain.CashSession;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cash sessions of the store's drawers (SC-052); every statement is scoped by the tenant it is
 * given, after {@link #lockMember} authorized the actor.
 */
@Repository
public class CashSessionRepository {
  private final JdbcTemplate jdbc;

  public CashSessionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The actor's role in the store (OWNER or STAFF) with an active, verified identity, locking the
   * store while the transaction lasts ({@code forUpdate} serializes the store's cash writes); empty
   * for anyone else.
   */
  public Optional<String> lockMember(UUID actor, UUID tenant, boolean forUpdate) {
    return jdbc
        .queryForList(
            """
            SELECT m.role FROM store s
            JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=?
              AND m.role IN ('OWNER','STAFF')
            JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            WHERE s.tenant_id=?
            """
                + (forUpdate ? " FOR UPDATE OF s FOR SHARE OF m, u" : " FOR SHARE OF s, m, u"),
            String.class,
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  public record Drawer(UUID id, String name) {}

  /** The store's drawers (CASH accounts), the default one first. */
  public List<Drawer> drawers(UUID tenant) {
    return jdbc.query(
        """
        SELECT id, name FROM finance_account WHERE tenant_id=? AND type='CASH'
        ORDER BY system_key IS NULL, created_at, id
        """,
        (row, index) -> new Drawer(row.getObject("id", UUID.class), row.getString("name")),
        tenant);
  }

  public record Destination(UUID id, String name, String type) {}

  /** Accounts a drop can go to or a top-up come from: every one but Mercado Pago (read-only). */
  public List<Destination> destinations(UUID tenant) {
    return jdbc.query(
        """
        SELECT id, name, type FROM finance_account
        WHERE tenant_id=? AND system_key IS DISTINCT FROM 'MERCADO_PAGO'
        ORDER BY system_key IS NULL, created_at, id
        """,
        (row, index) ->
            new Destination(
                row.getObject("id", UUID.class), row.getString("name"), row.getString("type")),
        tenant);
  }

  public boolean insert(
      UUID tenant,
      UUID id,
      UUID account,
      UUID request,
      BigDecimal opening,
      UUID actor,
      Instant now,
      UUID reopenedFrom,
      String reopenReason) {
    return jdbc.update(
            """
            INSERT INTO cash_session(id,tenant_id,account_id,status,request_id,opening_amount,
              opened_by,opened_at,reopened_from,reopen_reason)
            VALUES (?,?,?,'OPEN',?,?,?,?,?,?)
            ON CONFLICT (tenant_id, request_id) DO NOTHING
            """,
            id,
            tenant,
            account,
            request,
            opening,
            actor,
            Timestamp.from(now),
            reopenedFrom,
            reopenReason)
        == 1;
  }

  public boolean close(
      UUID tenant,
      UUID id,
      UUID request,
      BigDecimal counted,
      BigDecimal expected,
      UUID actor,
      Instant now) {
    return jdbc.update(
            """
            UPDATE cash_session SET status='CLOSED', close_request_id=?, counted_amount=?,
              expected_amount=?, closed_by=?, closed_at=?
            WHERE tenant_id=? AND id=? AND status='OPEN'
            """,
            request,
            counted,
            expected,
            actor,
            Timestamp.from(now),
            tenant,
            id)
        == 1;
  }

  public Optional<CashSession> byId(UUID tenant, UUID id) {
    return one(" WHERE c.tenant_id=? AND c.id=?", tenant, id);
  }

  public Optional<CashSession> lock(UUID tenant, UUID id) {
    return one(" WHERE c.tenant_id=? AND c.id=? FOR UPDATE OF c", tenant, id);
  }

  public Optional<CashSession> byRequest(UUID tenant, UUID request) {
    return one(" WHERE c.tenant_id=? AND c.request_id=?", tenant, request);
  }

  public Optional<CashSession> openFor(UUID tenant, UUID account) {
    return one(" WHERE c.tenant_id=? AND c.account_id=? AND c.status='OPEN'", tenant, account);
  }

  public Optional<CashSession> reopenOf(UUID tenant, UUID closed) {
    return one(" WHERE c.tenant_id=? AND c.reopened_from=?", tenant, closed);
  }

  /** The store's open sessions, by drawer name. */
  public List<CashSession> openSessions(UUID tenant) {
    return jdbc.query(
        SELECT + " WHERE c.tenant_id=? AND c.status='OPEN' ORDER BY a.name, c.id",
        CashSessionRepository::session,
        tenant);
  }

  /** The store's sessions, newest first, at most {@code limit}. */
  public List<CashSession> recent(UUID tenant, int limit) {
    return jdbc.query(
        SELECT + " WHERE c.tenant_id=? ORDER BY c.opened_at DESC, c.id DESC LIMIT ?",
        CashSessionRepository::session,
        tenant,
        limit);
  }

  /** The movements recorded in a session, oldest first. */
  public List<CashSession.Movement> movements(UUID tenant, UUID session) {
    return jdbc.query(
        """
        SELECT m.id, m.kind, m.amount, m.description, u.email_original, m.created_at
        FROM account_movement m JOIN identity_user u ON u.id=m.created_by
        WHERE m.tenant_id=? AND m.cash_session_id=? ORDER BY m.created_at, m.id
        """,
        (row, index) ->
            new CashSession.Movement(
                row.getObject("id", UUID.class),
                row.getString("kind"),
                row.getBigDecimal("amount").setScale(2),
                row.getString("description"),
                row.getString("email_original"),
                instant(row, "created_at")),
        tenant,
        session);
  }

  private Optional<CashSession> one(String where, Object... arguments) {
    return jdbc.query(SELECT + where, CashSessionRepository::session, arguments).stream()
        .findFirst();
  }

  private static final String SELECT =
      """
      SELECT c.id, c.account_id, a.name AS account_name, c.status, c.opening_amount,
        o.email_original AS opened_by, c.opened_at, c.reopened_from, c.reopen_reason,
        c.counted_amount, c.expected_amount, k.email_original AS closed_by, c.closed_at,
        c.close_request_id
      FROM cash_session c
      JOIN finance_account a ON a.id=c.account_id AND a.tenant_id=c.tenant_id
      JOIN identity_user o ON o.id=c.opened_by
      LEFT JOIN identity_user k ON k.id=c.closed_by""";

  private static CashSession session(ResultSet row, int index) throws SQLException {
    BigDecimal counted = row.getBigDecimal("counted_amount");
    BigDecimal expected = row.getBigDecimal("expected_amount");
    return new CashSession(
        row.getObject("id", UUID.class),
        row.getObject("account_id", UUID.class),
        row.getString("account_name"),
        row.getString("status"),
        row.getBigDecimal("opening_amount").setScale(2),
        row.getString("opened_by"),
        instant(row, "opened_at"),
        row.getObject("reopened_from", UUID.class),
        row.getString("reopen_reason"),
        counted == null ? null : counted.setScale(2),
        expected == null ? null : expected.setScale(2),
        row.getString("closed_by"),
        instant(row, "closed_at"),
        row.getObject("close_request_id", UUID.class));
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
