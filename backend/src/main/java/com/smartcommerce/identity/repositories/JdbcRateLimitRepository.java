package com.smartcommerce.identity.repositories;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL admission storage; the caller holds one transaction across all buckets. */
@Repository
public class JdbcRateLimitRepository {
  private final JdbcTemplate jdbc;

  public JdbcRateLimitRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void lockAdmission() {
    jdbc.execute("SELECT pg_advisory_xact_lock(736002)");
  }

  public void deleteExpired(Instant now) {
    jdbc.update("DELETE FROM identity_rate_limit WHERE expires_at <= ?", Timestamp.from(now));
  }

  public Optional<Integer> attempts(String key) {
    return jdbc
        .queryForList(
            "SELECT attempts FROM identity_rate_limit WHERE bucket_key = ?", Integer.class, key)
        .stream()
        .findFirst();
  }

  public long countBuckets() {
    Long count = jdbc.queryForObject("SELECT count(*) FROM identity_rate_limit", Long.class);
    return count == null ? Long.MAX_VALUE : count;
  }

  public void increment(String key, Instant now, Instant expires) {
    jdbc.update(
        """
        INSERT INTO identity_rate_limit(bucket_key, window_start, attempts, expires_at)
        VALUES (?, ?, 1, ?)
        ON CONFLICT(bucket_key) DO UPDATE SET attempts = identity_rate_limit.attempts + 1
        """,
        key,
        Timestamp.from(now),
        Timestamp.from(expires));
  }

  public void refund(String key) {
    jdbc.update(
        "UPDATE identity_rate_limit SET attempts = greatest(attempts - 1, 0) WHERE bucket_key = ?",
        key);
  }
}
