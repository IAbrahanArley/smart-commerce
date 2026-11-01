package com.smartcommerce.finance.repositories;

import com.smartcommerce.finance.domain.AccountMovement;
import com.smartcommerce.finance.domain.FinanceAccount;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Accounts of the store and their movements (SC-050, ADR-0019 C2). Every read and write filters by
 * the tenant the caller has already authorized through {@link #lockOwnedStore}; movements are only
 * ever inserted (the database refuses updates and deletes).
 */
@Repository
public class AccountRepository {
  private static final String ACCOUNTS =
      """
      SELECT a.id, a.type, a.name, a.system_key, COALESCE(SUM(m.amount), 0) AS balance
      FROM finance_account a LEFT JOIN account_movement m ON m.account_id=a.id AND m.tenant_id=a.tenant_id
      WHERE a.tenant_id=?
      """;
  private static final String ORDER =
      """
       GROUP BY a.id ORDER BY CASE a.system_key WHEN 'DEFAULT_CASH' THEN 0 WHEN 'MERCADO_PAGO' THEN 1
       ELSE 2 END, a.created_at, a.id
      """;

  private final JdbcTemplate jdbc;

  public AccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The store, locked while the caller's transaction lasts, only for its OWNER with an active,
   * verified identity. {@code forUpdate} serializes the writes of one store (account limit, keys).
   */
  public boolean lockOwnedStore(UUID actor, UUID tenant, boolean forUpdate) {
    return !jdbc.queryForList(
            """
            SELECT s.id FROM store s
            JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=? AND m.role='OWNER'
            JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            WHERE s.tenant_id=?
            """
                + (forUpdate ? " FOR UPDATE OF s FOR SHARE OF m, u" : " FOR SHARE OF s, m, u"),
            UUID.class,
            actor,
            tenant)
        .isEmpty();
  }

  public boolean providerConnected(UUID tenant) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM payment_account WHERE tenant_id=? AND status='CONNECTED')",
            Boolean.class,
            tenant));
  }

  /** Creates the platform's own account of this kind once; later calls do nothing. */
  public void ensureSystem(UUID tenant, String systemKey, String type, String name, Instant now) {
    jdbc.update(
        """
        INSERT INTO finance_account(id, tenant_id, type, name, system_key, created_at)
        VALUES (?,?,?,?,?,?) ON CONFLICT (tenant_id, system_key) DO NOTHING
        """,
        UUID.randomUUID(),
        tenant,
        type,
        name,
        systemKey,
        Timestamp.from(now));
  }

  public long count(UUID tenant) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM finance_account WHERE tenant_id=?", Long.class, tenant);
  }

  /** Inserts once per request id; returns whether this call created it. */
  public boolean insertAccount(
      UUID tenant, UUID id, UUID request, String type, String name, UUID actor, Instant now) {
    return jdbc.update(
            """
            INSERT INTO finance_account(id, tenant_id, type, name, request_id, created_by, created_at)
            VALUES (?,?,?,?,?,?,?) ON CONFLICT (tenant_id, request_id) DO NOTHING
            """,
            id,
            tenant,
            type,
            name,
            request,
            actor,
            Timestamp.from(now))
        == 1;
  }

  public Optional<FinanceAccount> accountByRequest(UUID tenant, UUID request) {
    return jdbc
        .query(
            ACCOUNTS + " AND a.request_id=?" + ORDER, AccountRepository::account, tenant, request)
        .stream()
        .findFirst();
  }

  public List<FinanceAccount> accounts(UUID tenant) {
    return jdbc.query(ACCOUNTS + ORDER, AccountRepository::account, tenant);
  }

  public Optional<FinanceAccount> account(UUID tenant, UUID id) {
    return jdbc
        .query(ACCOUNTS + " AND a.id=?" + ORDER, AccountRepository::account, tenant, id)
        .stream()
        .findFirst();
  }

  /** Inserts once per movement key; returns whether this call created it. */
  public boolean insertMovement(
      UUID tenant,
      UUID account,
      String key,
      String kind,
      BigDecimal amount,
      LocalDate occurredOn,
      String description,
      UUID transfer,
      UUID actor,
      Instant now) {
    return jdbc.update(
            """
            INSERT INTO account_movement(id, tenant_id, account_id, movement_key, kind, amount,
              occurred_on, description, transfer_id, created_by, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id, movement_key) DO NOTHING
            """,
            UUID.randomUUID(),
            tenant,
            account,
            key,
            kind,
            amount,
            Date.valueOf(occurredOn),
            description,
            transfer,
            actor,
            Timestamp.from(now))
        == 1;
  }

  /** What a movement key already holds, to tell a retry from a different request. */
  public record Stored(UUID account, BigDecimal amount, LocalDate occurredOn, String description) {}

  public Optional<Stored> movement(UUID tenant, String key) {
    return jdbc
        .query(
            "SELECT account_id, amount, occurred_on, description FROM account_movement"
                + " WHERE tenant_id=? AND movement_key=?",
            (row, index) ->
                new Stored(
                    row.getObject("account_id", UUID.class),
                    row.getBigDecimal("amount"),
                    row.getDate("occurred_on").toLocalDate(),
                    row.getString("description")),
            tenant,
            key)
        .stream()
        .findFirst();
  }

  public BigDecimal balanceBefore(UUID tenant, UUID account, LocalDate day) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(amount), 0) FROM account_movement"
            + " WHERE tenant_id=? AND account_id=? AND occurred_on<?",
        BigDecimal.class,
        tenant,
        account,
        Date.valueOf(day));
  }

  /**
   * The account's movements from {@code from} to {@code to}, newest first, each with the balance
   * right after it (counted over the whole history in day, creation and id order) and, for a
   * transfer, the account on the other side. At most {@code limit} rows.
   */
  public List<AccountMovement> statement(
      UUID tenant, UUID account, LocalDate from, LocalDate to, int limit) {
    return jdbc.query(
        """
        SELECT x.* FROM (
          SELECT m.id, m.kind, m.amount, m.occurred_on, m.description, m.transfer_id, m.created_at,
                 o.account_id AS counterpart,
                 SUM(m.amount) OVER (ORDER BY m.occurred_on, m.created_at, m.id) AS balance_after
          FROM account_movement m
          LEFT JOIN account_movement o
            ON o.transfer_id=m.transfer_id AND o.id<>m.id AND o.tenant_id=m.tenant_id
          WHERE m.tenant_id=? AND m.account_id=?) x
        WHERE x.occurred_on BETWEEN ? AND ?
        ORDER BY x.occurred_on DESC, x.created_at DESC, x.id DESC LIMIT ?
        """,
        (row, index) ->
            new AccountMovement(
                row.getObject("id", UUID.class),
                row.getString("kind"),
                row.getBigDecimal("amount"),
                row.getDate("occurred_on").toLocalDate(),
                row.getString("description"),
                row.getObject("transfer_id", UUID.class),
                row.getObject("counterpart", UUID.class),
                row.getTimestamp("created_at").toInstant(),
                row.getBigDecimal("balance_after")),
        tenant,
        account,
        Date.valueOf(from),
        Date.valueOf(to),
        limit);
  }

  private static FinanceAccount account(ResultSet row, int index) throws SQLException {
    return new FinanceAccount(
        row.getObject("id", UUID.class),
        row.getString("type"),
        row.getString("name"),
        row.getString("system_key"),
        row.getBigDecimal("balance").setScale(2));
  }
}
