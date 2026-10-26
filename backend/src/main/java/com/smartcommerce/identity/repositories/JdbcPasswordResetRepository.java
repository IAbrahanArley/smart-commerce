package com.smartcommerce.identity.repositories;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Password reset proof persistence (MVP block 1a). Only token digests are stored. The caller owns
 * the transaction and locks the user before consuming a proof.
 */
@Repository
public class JdbcPasswordResetRepository {
  private final JdbcTemplate jdbc;

  public JdbcPasswordResetRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Instant> latestIssue(UUID user) {
    return jdbc
        .queryForList(
            "SELECT issued_at FROM identity_password_reset WHERE user_id=? ORDER BY issued_at DESC LIMIT 1",
            Timestamp.class,
            user)
        .stream()
        .findFirst()
        .map(Timestamp::toInstant);
  }

  public void deleteForUser(UUID user) {
    jdbc.update("DELETE FROM identity_password_reset WHERE user_id=?", user);
  }

  public void insert(String digest, UUID user, Instant issued, Instant expires) {
    jdbc.update(
        "INSERT INTO identity_password_reset(token_digest,user_id,issued_at,expires_at) VALUES(?,?,?,?)",
        digest,
        user,
        Timestamp.from(issued),
        Timestamp.from(expires));
  }

  /** The user of an unused, unexpired proof, only while the account is active and verified. */
  public Optional<UUID> findEligibleUser(String digest, Instant now) {
    return jdbc
        .queryForList(
            """
            SELECT r.user_id FROM identity_password_reset r JOIN identity_user u ON u.id=r.user_id
            WHERE r.token_digest=? AND r.consumed_at IS NULL AND r.expires_at>?
              AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            """,
            UUID.class,
            digest,
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  public boolean consume(String digest, UUID user, Instant now) {
    return jdbc.update(
            "UPDATE identity_password_reset SET consumed_at=? WHERE token_digest=? AND user_id=? AND consumed_at IS NULL AND expires_at>?",
            Timestamp.from(now),
            digest,
            user,
            Timestamp.from(now))
        == 1;
  }

  public void deleteExpired(Instant threshold) {
    jdbc.update(
        "DELETE FROM identity_password_reset WHERE expires_at < ? OR consumed_at < ?",
        Timestamp.from(threshold),
        Timestamp.from(threshold));
  }
}
