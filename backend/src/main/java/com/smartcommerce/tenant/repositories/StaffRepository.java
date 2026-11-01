package com.smartcommerce.tenant.repositories;

import com.smartcommerce.tenant.domain.StaffRecords.Member;
import com.smartcommerce.tenant.domain.StaffRecords.OpenInvitation;
import com.smartcommerce.tenant.domain.StaffRecords.Redeemable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Store employees and their invitations (SC-049, ADR-0019 C1). Callers check the OWNER first
 * (through {@link TenantAccessRepository}) and own the transaction; nothing here trusts a tenant on
 * its own. Only token digests are stored.
 */
@Repository
public class StaffRepository {
  private final JdbcTemplate jdbc;

  public StaffRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Distinct people working for {@code owner} in any of his companies, other than {@code except}.
   */
  public long staffMembers(UUID owner, UUID except) {
    return jdbc.queryForObject(
        """
        SELECT count(DISTINCT m.user_id) FROM tenant_membership m
        WHERE m.role='STAFF' AND m.user_id<>?
          AND m.tenant_id IN (SELECT tenant_id FROM tenant_membership WHERE user_id=? AND role='OWNER')
        """,
        Long.class,
        except,
        owner);
  }

  public UUID ownerOf(UUID tenant) {
    return jdbc.queryForObject(
        "SELECT user_id FROM tenant_membership WHERE tenant_id=? AND role='OWNER'",
        UUID.class,
        tenant);
  }

  public String storeName(UUID tenant) {
    return jdbc.queryForObject("SELECT name FROM store WHERE tenant_id=?", String.class, tenant);
  }

  /**
   * People working for {@code owner} or invited to, counted once each over all of his companies:
   * STAFF members plus unexpired open invitations to e-mails that are not already his staff.
   */
  public long staffAndOpenInvitations(UUID owner, Instant now) {
    return jdbc.queryForObject(
        """
        WITH owned AS (
          SELECT tenant_id FROM tenant_membership WHERE user_id=? AND role='OWNER'),
        staff AS (
          SELECT DISTINCT u.id, u.email_key FROM tenant_membership m
          JOIN identity_user u ON u.id=m.user_id
          WHERE m.role='STAFF' AND m.tenant_id IN (SELECT tenant_id FROM owned)),
        invited AS (
          SELECT DISTINCT i.email_key FROM staff_invitation i
          WHERE i.tenant_id IN (SELECT tenant_id FROM owned)
            AND i.accepted_at IS NULL AND i.canceled_at IS NULL AND i.expires_at>?
            AND i.email_key NOT IN (SELECT email_key FROM staff))
        SELECT (SELECT count(*) FROM staff) + (SELECT count(*) FROM invited)
        """,
        Long.class,
        owner,
        Timestamp.from(now));
  }

  /**
   * Whether this person already takes a slot of {@code owner}'s plan: an employee of one of his
   * companies, or invited to one with an unexpired open invitation. Such a person costs no new
   * slot.
   */
  public boolean alreadyCounted(UUID owner, String emailKey, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            WITH owned AS (
              SELECT tenant_id FROM tenant_membership WHERE user_id=? AND role='OWNER')
            SELECT EXISTS (SELECT 1 FROM tenant_membership m JOIN identity_user u ON u.id=m.user_id
                           WHERE m.role='STAFF' AND u.email_key=?
                             AND m.tenant_id IN (SELECT tenant_id FROM owned))
                OR EXISTS (SELECT 1 FROM staff_invitation i
                           WHERE i.email_key=? AND i.accepted_at IS NULL AND i.canceled_at IS NULL
                             AND i.expires_at>? AND i.tenant_id IN (SELECT tenant_id FROM owned))
            """,
            Boolean.class,
            owner,
            emailKey,
            emailKey,
            Timestamp.from(now)));
  }

  /** Whether the e-mail already belongs to someone in the company (its OWNER or an employee). */
  public boolean isMember(UUID tenant, String emailKey) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (SELECT 1 FROM tenant_membership m JOIN identity_user u ON u.id=m.user_id
                           WHERE m.tenant_id=? AND u.email_key=?)
            """,
            Boolean.class,
            tenant,
            emailKey));
  }

  public List<Member> members(UUID tenant) {
    return jdbc.query(
        """
        SELECT m.user_id, u.email_original, m.created_at FROM tenant_membership m
        JOIN identity_user u ON u.id=m.user_id
        WHERE m.tenant_id=? AND m.role='STAFF' ORDER BY m.created_at, m.user_id
        """,
        (row, index) ->
            new Member(
                row.getObject("user_id", UUID.class),
                row.getString("email_original"),
                row.getTimestamp("created_at").toInstant()),
        tenant);
  }

  public List<OpenInvitation> openInvitations(UUID tenant) {
    return jdbc.query(
        """
        SELECT id, email_original, created_at, expires_at FROM staff_invitation
        WHERE tenant_id=? AND accepted_at IS NULL AND canceled_at IS NULL
        ORDER BY created_at, id
        """,
        (row, index) ->
            new OpenInvitation(
                row.getObject("id", UUID.class),
                row.getString("email_original"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("expires_at").toInstant()),
        tenant);
  }

  /** Cancels the open invitation of this e-mail, if any (a new one replaces it). */
  public void cancelOpenFor(UUID tenant, String emailKey, Instant now) {
    jdbc.update(
        "UPDATE staff_invitation SET canceled_at=? WHERE tenant_id=? AND email_key=?"
            + " AND accepted_at IS NULL AND canceled_at IS NULL",
        Timestamp.from(now),
        tenant,
        emailKey);
  }

  public void insertInvitation(
      UUID id,
      UUID tenant,
      String emailKey,
      String emailOriginal,
      String digest,
      UUID invitedBy,
      Instant now,
      Instant expires) {
    jdbc.update(
        """
        INSERT INTO staff_invitation
          (id, tenant_id, email_key, email_original, token_digest, invited_by, created_at, expires_at)
        VALUES (?,?,?,?,?,?,?,?)
        """,
        id,
        tenant,
        emailKey,
        emailOriginal,
        digest,
        invitedBy,
        Timestamp.from(now),
        Timestamp.from(expires));
  }

  public boolean cancelInvitation(UUID tenant, UUID invitation, Instant now) {
    return jdbc.update(
            "UPDATE staff_invitation SET canceled_at=? WHERE id=? AND tenant_id=?"
                + " AND accepted_at IS NULL AND canceled_at IS NULL",
            Timestamp.from(now),
            invitation,
            tenant)
        == 1;
  }

  /** The open invitation behind a token digest, locked; expiry is left to the caller. */
  public Optional<Redeemable> lockOpenByDigest(String digest) {
    return jdbc
        .query(
            """
            SELECT i.id, i.tenant_id, i.email_key, i.expires_at, s.name FROM staff_invitation i
            JOIN store s ON s.tenant_id=i.tenant_id
            WHERE i.token_digest=? AND i.accepted_at IS NULL AND i.canceled_at IS NULL
            FOR UPDATE OF i
            """,
            (row, index) ->
                new Redeemable(
                    row.getObject("id", UUID.class),
                    row.getObject("tenant_id", UUID.class),
                    row.getString("email_key"),
                    row.getTimestamp("expires_at").toInstant(),
                    row.getString("name")),
            digest)
        .stream()
        .findFirst();
  }

  /** The comparison key of an active, verified account, locked for the acceptance. */
  public Optional<String> lockVerifiedEmailKey(UUID user) {
    return jdbc
        .queryForList(
            "SELECT email_key FROM identity_user WHERE id=? AND status='ACTIVE'"
                + " AND verified_at IS NOT NULL FOR SHARE",
            String.class,
            user)
        .stream()
        .findFirst();
  }

  /** Adds the employee; false when the account already belongs to the company. */
  public boolean addStaff(UUID tenant, UUID user, Instant now) {
    return jdbc.update(
            "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at)"
                + " VALUES (?,?,'STAFF',?) ON CONFLICT (tenant_id, user_id) DO NOTHING",
            tenant,
            user,
            Timestamp.from(now))
        == 1;
  }

  public void markAccepted(UUID invitation, UUID user, Instant now) {
    jdbc.update(
        "UPDATE staff_invitation SET accepted_at=?, accepted_by=? WHERE id=?",
        Timestamp.from(now),
        user,
        invitation);
  }

  /**
   * Removes an employee's access at once (the next request finds no membership) and records who
   * revoked it on the accepted invitation; false when the user is not an employee of the company.
   */
  public boolean revokeStaff(UUID tenant, UUID user, UUID actor, Instant now) {
    boolean removed =
        jdbc.update(
                "DELETE FROM tenant_membership WHERE tenant_id=? AND user_id=? AND role='STAFF'",
                tenant,
                user)
            == 1;
    if (removed)
      jdbc.update(
          "UPDATE staff_invitation SET access_revoked_at=?, revoked_by=?"
              + " WHERE tenant_id=? AND accepted_by=? AND access_revoked_at IS NULL",
          Timestamp.from(now),
          actor,
          tenant,
          user);
    return removed;
  }
}
