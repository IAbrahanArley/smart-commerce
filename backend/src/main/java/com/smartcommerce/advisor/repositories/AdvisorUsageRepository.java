package com.smartcommerce.advisor.repositories;

import com.smartcommerce.advisor.domain.AdvisorUsage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * Assistant usage rows (SC-025, ADR-0016 A8, A9): metadata only. Quota checks serialize per store
 * and for the platform cap with transaction-scoped advisory locks, always taken in the same order
 * (global, then store) so concurrent questions never overshoot a limit nor deadlock.
 */
@Repository
public class AdvisorUsageRepository {
  /** Outcomes that consume a question of the store's quota (provider failures do not). */
  private static final String COUNTED = "('PENDING','ANSWERED','REFUSED','UNGROUNDED')";

  private final JdbcTemplate jdbc;

  public AdvisorUsageRepository(JdbcTemplate jdbc) {
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

  public void lockGlobal() {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended('advisor-usage:global',0))",
        (RowCallbackHandler) row -> {});
  }

  public void lockStore(UUID tenant) {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
        (RowCallbackHandler) row -> {},
        "advisor-usage:" + tenant);
  }

  public long countedSince(UUID tenant, Instant since) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM advisor_usage WHERE tenant_id=? AND created_at>=? AND outcome IN "
                + COUNTED,
            Long.class,
            tenant,
            utc(since));
    return count == null ? 0 : count;
  }

  public BigDecimal costSince(Instant since) {
    BigDecimal cost =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(estimated_cost_usd),0) FROM advisor_usage WHERE created_at>=?",
            BigDecimal.class,
            utc(since));
    return cost == null ? BigDecimal.ZERO : cost;
  }

  public void insertPending(UUID id, UUID tenant, UUID user, Instant now) {
    jdbc.update(
        "INSERT INTO advisor_usage(id,tenant_id,user_id,created_at,outcome) VALUES (?,?,?,?,'PENDING')",
        id,
        tenant,
        user,
        utc(now));
  }

  /** Closes a pending row once; a second call changes nothing and returns false. */
  public boolean finish(UUID id, UUID tenant, AdvisorUsage usage, Instant now) {
    return jdbc.update(
            """
            UPDATE advisor_usage SET outcome=?, model=?, input_tokens=?, cache_read_tokens=?,
              output_tokens=?, tool_calls=?, tools_used=?, estimated_cost_usd=?, finished_at=?
            WHERE id=? AND tenant_id=? AND outcome='PENDING'
            """,
            usage.outcome(),
            usage.model(),
            usage.inputTokens(),
            usage.cacheReadTokens(),
            usage.outputTokens(),
            usage.toolCalls(),
            usage.toolsUsed(),
            usage.estimatedCostUsd(),
            utc(now),
            id,
            tenant)
        == 1;
  }

  /** Questions left PENDING by a crashed process stop counting after {@code before}. */
  public int cancelStale(Instant before, Instant now) {
    return jdbc.update(
        """
        UPDATE advisor_usage SET outcome='CANCELLED', finished_at=?
        WHERE id IN (SELECT id FROM advisor_usage WHERE outcome='PENDING' AND created_at<? LIMIT 500)
        """,
        utc(now),
        utc(before));
  }

  /** A9 retention, bounded per call. */
  public int deleteOlderThan(Instant cutoff) {
    return jdbc.update(
        """
        DELETE FROM advisor_usage WHERE id IN (
          SELECT id FROM advisor_usage WHERE created_at<? LIMIT 1000)
        """,
        utc(cutoff));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
