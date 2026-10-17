package com.smartcommerce.identity.repositories;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Email proof persistence. Caller owns the transaction and locks the user before consuming proof.
 */
@Repository
public class JdbcVerificationRepository {
  private final JdbcTemplate jdbc;

  public JdbcVerificationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Instant> latestIssue(UUID user) {
    return jdbc
        .queryForList(
            "SELECT issued_at FROM identity_email_verification WHERE user_id=? ORDER BY issued_at DESC LIMIT 1",
            Timestamp.class,
            user)
        .stream()
        .findFirst()
        .map(Timestamp::toInstant);
  }

  public void deleteForUser(UUID user) {
    jdbc.update("DELETE FROM identity_email_verification WHERE user_id=?", user);
  }

  public void insert(String digest, UUID user, Instant issued, Instant expires) {
    jdbc.update(
        "INSERT INTO identity_email_verification(token_digest,user_id,issued_at,expires_at) VALUES(?,?,?,?)",
        digest,
        user,
        Timestamp.from(issued),
        Timestamp.from(expires));
  }

  public Optional<UUID> findEligibleUser(String digest, Instant now, Instant oldestRegistration) {
    return jdbc
        .queryForList(
            """
        SELECT v.user_id FROM identity_email_verification v JOIN identity_user u ON u.id=v.user_id
        WHERE v.token_digest=? AND v.consumed_at IS NULL AND v.expires_at>? AND u.status='PENDING_EMAIL' AND u.created_at>?
        """,
            UUID.class,
            digest,
            Timestamp.from(now),
            Timestamp.from(oldestRegistration))
        .stream()
        .findFirst();
  }

  public boolean consume(String digest, UUID user, Instant now) {
    return jdbc.update(
            "UPDATE identity_email_verification SET consumed_at=? WHERE token_digest=? AND user_id=? AND consumed_at IS NULL AND expires_at>?",
            Timestamp.from(now),
            digest,
            user,
            Timestamp.from(now))
        == 1;
  }

  public void deleteExpired(Instant threshold) {
    jdbc.update(
        "DELETE FROM identity_email_verification WHERE expires_at < ? OR consumed_at < ?",
        Timestamp.from(threshold),
        Timestamp.from(threshold));
  }
}
