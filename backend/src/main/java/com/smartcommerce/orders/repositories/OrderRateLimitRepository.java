package com.smartcommerce.orders.repositories;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Checkout admission counters (ADR-0012 D4). Keys arrive already hashed by the caller; this table
 * never sees an IP address. Fixed windows: the first attempt opens the window until expiry.
 */
@Repository
public class OrderRateLimitRepository {
  private final JdbcTemplate jdbc;

  public OrderRateLimitRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Counts one attempt atomically and returns the attempts in the current window. */
  public int increment(String key, Instant now, Instant expiresAt) {
    jdbc.update(
        "DELETE FROM order_rate_limit WHERE bucket_key=? AND expires_at<=?",
        key,
        now.atOffset(ZoneOffset.UTC));
    Integer attempts =
        jdbc.queryForObject(
            """
            INSERT INTO order_rate_limit(bucket_key,window_start,attempts,expires_at)
            VALUES (?,?,1,?)
            ON CONFLICT (bucket_key) DO UPDATE SET attempts=order_rate_limit.attempts+1
            RETURNING attempts
            """,
            Integer.class,
            key,
            now.atOffset(ZoneOffset.UTC),
            expiresAt.atOffset(ZoneOffset.UTC));
    return attempts == null ? Integer.MAX_VALUE : attempts;
  }

  /** Housekeeping of expired windows; bounded so a checkout never pays for a large cleanup. */
  public void deleteExpired(Instant now) {
    jdbc.update(
        """
        DELETE FROM order_rate_limit WHERE bucket_key IN (
          SELECT bucket_key FROM order_rate_limit WHERE expires_at<=? LIMIT 100)
        """,
        now.atOffset(ZoneOffset.UTC));
  }
}
