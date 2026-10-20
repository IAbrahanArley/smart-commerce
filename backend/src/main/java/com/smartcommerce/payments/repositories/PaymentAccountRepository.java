package com.smartcommerce.payments.repositories;

import com.smartcommerce.payments.domain.PaymentAccountRecord;
import com.smartcommerce.payments.domain.PendingAuthorization;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Provider accounts and pending OAuth attempts (SC-012). Token and verifier columns arrive and
 * leave encrypted; every query is scoped by the tenant it is given.
 */
@Repository
public class PaymentAccountRepository {
  private final JdbcTemplate jdbc;

  public PaymentAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

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

  /** Keeps a single pending attempt per user and store: a new one replaces the previous. */
  public void savePendingAuthorization(
      byte[] stateHash,
      UUID tenant,
      UUID user,
      byte[] codeVerifier,
      Instant now,
      Instant expiresAt) {
    jdbc.update(
        "DELETE FROM payment_oauth_state WHERE tenant_id=? AND user_id=? AND used_at IS NULL",
        tenant,
        user);
    jdbc.update(
        """
        INSERT INTO payment_oauth_state(state_hash,tenant_id,user_id,code_verifier,created_at,expires_at)
        VALUES (?,?,?,?,?,?)
        """,
        stateHash,
        tenant,
        user,
        codeVerifier,
        utc(now),
        utc(expiresAt));
  }

  public Optional<PendingAuthorization> lockPendingAuthorization(byte[] stateHash) {
    return jdbc
        .query(
            """
            SELECT tenant_id,user_id,code_verifier,expires_at,used_at FROM payment_oauth_state
            WHERE state_hash=? FOR UPDATE
            """,
            (row, index) ->
                new PendingAuthorization(
                    row.getObject("tenant_id", UUID.class),
                    row.getObject("user_id", UUID.class),
                    row.getBytes("code_verifier"),
                    instant(row, "expires_at"),
                    instant(row, "used_at")),
            stateHash)
        .stream()
        .findFirst();
  }

  public void markUsed(byte[] stateHash, Instant now) {
    jdbc.update(
        "UPDATE payment_oauth_state SET used_at=? WHERE state_hash=? AND used_at IS NULL",
        utc(now),
        stateHash);
  }

  /** Housekeeping, bounded so a request never pays for a large cleanup. */
  public void deleteExpiredAuthorizations(Instant now) {
    jdbc.update(
        """
        DELETE FROM payment_oauth_state WHERE state_hash IN (
          SELECT state_hash FROM payment_oauth_state WHERE expires_at<=? LIMIT 100)
        """,
        utc(now.minusSeconds(3600)));
  }

  public Optional<PaymentAccountRecord> find(UUID tenant) {
    return jdbc
        .query(SELECT + " WHERE tenant_id=?", PaymentAccountRepository::account, tenant)
        .stream()
        .findFirst();
  }

  public Optional<PaymentAccountRecord> lock(UUID tenant) {
    return jdbc
        .query(SELECT + " WHERE tenant_id=? FOR UPDATE", PaymentAccountRepository::account, tenant)
        .stream()
        .findFirst();
  }

  public void saveConnected(
      UUID tenant,
      String providerUserId,
      byte[] accessToken,
      byte[] refreshToken,
      Instant accessExpiresAt,
      int keyVersion,
      Boolean liveMode,
      UUID actor,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO payment_account(tenant_id,provider,provider_user_id,status,access_token,
          refresh_token,access_expires_at,key_version,live_mode,connected_by,connected_at)
        VALUES (?,'MERCADO_PAGO',?,'CONNECTED',?,?,?,?,?,?,?)
        ON CONFLICT (tenant_id) DO UPDATE SET provider_user_id=EXCLUDED.provider_user_id,
          status='CONNECTED', access_token=EXCLUDED.access_token,
          refresh_token=EXCLUDED.refresh_token, access_expires_at=EXCLUDED.access_expires_at,
          key_version=EXCLUDED.key_version, live_mode=EXCLUDED.live_mode,
          connected_by=EXCLUDED.connected_by, connected_at=EXCLUDED.connected_at,
          refreshed_at=NULL, closed_at=NULL, version=payment_account.version+1
        """,
        tenant,
        providerUserId,
        accessToken,
        refreshToken,
        utc(accessExpiresAt),
        keyVersion,
        liveMode,
        actor,
        utc(now));
  }

  /** Stores the rotated pair; only a CONNECTED row changes. */
  public boolean saveRefreshed(
      UUID tenant,
      byte[] accessToken,
      byte[] refreshToken,
      Instant accessExpiresAt,
      int keyVersion,
      Instant now) {
    return jdbc.update(
            """
            UPDATE payment_account SET access_token=?, refresh_token=?, access_expires_at=?,
              key_version=?, refreshed_at=?, version=version+1
            WHERE tenant_id=? AND status='CONNECTED'
            """,
            accessToken,
            refreshToken,
            utc(accessExpiresAt),
            keyVersion,
            utc(now),
            tenant)
        == 1;
  }

  /** Erases both tokens: {@code status} is DISCONNECTED (owner) or REVOKED (provider). */
  public boolean close(UUID tenant, String status, Instant now) {
    return jdbc.update(
            """
            UPDATE payment_account SET status=?, access_token=NULL, refresh_token=NULL,
              access_expires_at=NULL, key_version=NULL, closed_at=?, version=version+1
            WHERE tenant_id=? AND status='CONNECTED'
            """,
            status,
            utc(now),
            tenant)
        == 1;
  }

  /** Connected stores whose access token expires before {@code before}, oldest first. */
  public List<UUID> dueForRefresh(Instant before, int limit) {
    return jdbc.queryForList(
        """
        SELECT tenant_id FROM payment_account WHERE status='CONNECTED' AND access_expires_at<?
        ORDER BY access_expires_at LIMIT ?
        """,
        UUID.class,
        utc(before),
        limit);
  }

  private static final String SELECT =
      """
      SELECT provider_user_id,status,access_token,refresh_token,access_expires_at,connected_at,
        refreshed_at,closed_at FROM payment_account""";

  private static PaymentAccountRecord account(ResultSet row, int index) throws SQLException {
    return new PaymentAccountRecord(
        row.getString("provider_user_id"),
        row.getString("status"),
        row.getBytes("access_token"),
        row.getBytes("refresh_token"),
        instant(row, "access_expires_at"),
        instant(row, "connected_at"),
        instant(row, "refreshed_at"),
        instant(row, "closed_at"));
  }

  private static Instant instant(ResultSet row, String column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
