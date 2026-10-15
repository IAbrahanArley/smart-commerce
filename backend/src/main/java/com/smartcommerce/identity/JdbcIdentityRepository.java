package com.smartcommerce.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Internal persistence primitives, deliberately not exposed as registration/activation services.
 */
public final class JdbcIdentityRepository {
  private final JdbcTemplate jdbc;

  public JdbcIdentityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void createPending(UUID id, EmailAddress email, Instant now) {
    update(
        """
        INSERT INTO identity_user
          (id, email_original, email_key, status, auth_version, created_at, updated_at)
        VALUES (?, ?, ?, 'PENDING_EMAIL', 0, ?, ?)
        """,
        id,
        email.original(),
        email.comparisonKey(),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public Optional<IdentityUser> findUser(UUID id) {
    return jdbc.query("SELECT * FROM identity_user WHERE id = ?", this::user, id).stream()
        .findFirst();
  }

  public Optional<IdentityUser> findByEmail(EmailAddress email) {
    return jdbc
        .query("SELECT * FROM identity_user WHERE email_key = ?", this::user, email.comparisonKey())
        .stream()
        .findFirst();
  }

  public Optional<LocalCredential> findCredential(UUID userId) {
    return jdbc
        .query(
            "SELECT * FROM identity_local_credential WHERE user_id = ?",
            (rs, row) ->
                new LocalCredential(
                    rs.getObject("user_id", UUID.class),
                    new PasswordHash(rs.getString("password_hash")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            userId)
        .stream()
        .findFirst();
  }

  /** Row lock serializes a future status transition with credential persistence. */
  public boolean insertCredential(UUID userId, PasswordHash hash, Instant now) {
    return update(
            """
            WITH eligible AS (
              SELECT id FROM identity_user
              WHERE id = ? AND status = 'ACTIVE' AND verified_at IS NOT NULL
              FOR UPDATE
            )
            INSERT INTO identity_local_credential (user_id, password_hash, created_at, updated_at)
            SELECT id, ?, ?, ? FROM eligible
            """,
            userId,
            hash.encoded(),
            Timestamp.from(now),
            Timestamp.from(now))
        == 1;
  }

  /**
   * Rehash only: no password replacement/revocation service, and never overwrites a racing change.
   */
  public boolean upgradeCredentialHash(
      UUID userId,
      long expectedAuthVersion,
      PasswordHash expected,
      PasswordHash replacement,
      Instant now) {
    if (replacement.iterations() < expected.iterations()) {
      throw new IllegalArgumentException("Password hash security parameters cannot be downgraded");
    }
    return update(
            """
            WITH eligible AS (
              SELECT id FROM identity_user
              WHERE id = ? AND status = 'ACTIVE' AND verified_at IS NOT NULL AND auth_version = ?
              FOR UPDATE
            )
            UPDATE identity_local_credential AS credential
            SET password_hash = ?, updated_at = ?
            FROM eligible
            WHERE credential.user_id = eligible.id AND credential.password_hash = ?
            """,
            userId,
            expectedAuthVersion,
            replacement.encoded(),
            Timestamp.from(now),
            expected.encoded())
        == 1;
  }

  private int update(String sql, Object... arguments) {
    try {
      return jdbc.update(sql, arguments);
    } catch (DuplicateKeyException exception) {
      throw new DuplicateKeyException("Identity uniqueness constraint violated");
    } catch (DataIntegrityViolationException exception) {
      throw new DataIntegrityViolationException("Identity persistence constraint violated");
    } catch (DataAccessException exception) {
      // PostgreSQL error detail can contain an entire failing row, including email/hash.
      throw new DataAccessResourceFailureException("Identity persistence operation failed");
    }
  }

  private IdentityUser user(ResultSet rs, int row) throws SQLException {
    Timestamp verified = rs.getTimestamp("verified_at");
    return new IdentityUser(
        rs.getObject("id", UUID.class),
        EmailAddress.parse(rs.getString("email_original")),
        IdentityStatus.valueOf(rs.getString("status")),
        rs.getLong("auth_version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        verified == null ? null : verified.toInstant());
  }
}
