package com.smartcommerce.billing.repositories;

import com.smartcommerce.billing.domain.BillingAccount;
import com.smartcommerce.billing.domain.Plan;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** Billing accounts and the counts the plan limits compare against (SC-031, ADR-0017). */
@Repository
public class BillingRepository {
  private final JdbcTemplate jdbc;

  public BillingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Opens the trial for an owner who has no account yet; an existing account is left untouched. */
  public void ensure(UUID user, Instant now, Instant trialEndsAt) {
    jdbc.update(
        """
        INSERT INTO billing_account (user_id, status, trial_ends_at, created_at, updated_at)
        VALUES (?, 'TRIAL', ?, ?, ?) ON CONFLICT (user_id) DO NOTHING""",
        user,
        utc(trialEndsAt),
        utc(now),
        utc(now));
  }

  public Optional<BillingAccount> find(UUID user) {
    return jdbc
        .query(
            """
            SELECT user_id, status, plan, billing_cycle, trial_ends_at, current_period_end,
                   past_due_since
              FROM billing_account WHERE user_id=?""",
            (row, index) ->
                new BillingAccount(
                    row.getObject("user_id", UUID.class),
                    row.getString("status"),
                    row.getString("plan") == null ? null : Plan.valueOf(row.getString("plan")),
                    row.getString("billing_cycle"),
                    instant(row.getTimestamp("trial_ends_at")),
                    instant(row.getTimestamp("current_period_end")),
                    instant(row.getTimestamp("past_due_since"))),
            user)
        .stream()
        .findFirst();
  }

  public Optional<UUID> ownerOfTenant(UUID tenant) {
    return jdbc
        .queryForList(
            "SELECT user_id FROM tenant_membership WHERE tenant_id=? AND role='OWNER'",
            UUID.class,
            tenant)
        .stream()
        .findFirst();
  }

  public Optional<UUID> ownerOfStore(String slug) {
    return jdbc
        .queryForList(
            """
            SELECT m.user_id FROM store s
              JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.role='OWNER'
             WHERE s.slug=?""",
            UUID.class,
            slug)
        .stream()
        .findFirst();
  }

  public long companiesOwned(UUID user) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM tenant_membership WHERE user_id=? AND role='OWNER'",
            Long.class,
            user);
    return count == null ? 0 : count;
  }

  public long products(UUID tenant) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog_product WHERE tenant_id=?", Long.class, tenant);
    return count == null ? 0 : count;
  }

  /** Serializes limit checks of one owner, so two requests cannot both take the last slot. */
  public void lockOwner(UUID user) {
    lock("billing-owner:" + user);
  }

  public void lockProducts(UUID tenant) {
    lock("billing-products:" + tenant);
  }

  private void lock(String key) {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (RowCallbackHandler) row -> {}, key);
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
