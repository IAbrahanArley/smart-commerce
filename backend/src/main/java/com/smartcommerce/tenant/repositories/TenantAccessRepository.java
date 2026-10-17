package com.smartcommerce.tenant.repositories;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL row locks protect authorization until the surrounding service transaction ends. */
@Repository
public class TenantAccessRepository {
  private final JdbcTemplate jdbc;

  public TenantAccessRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean lockActiveIdentity(UUID actor) {
    return !jdbc.query(
            "SELECT id FROM identity_user WHERE id = ? AND status = 'ACTIVE' AND verified_at IS NOT NULL FOR SHARE",
            (row, index) -> row.getObject("id", UUID.class),
            actor)
        .isEmpty();
  }

  public Optional<UUID> lockOwnedStore(UUID actor, UUID tenant) {
    return jdbc
        .query(
            """
 SELECT s.id FROM store s
 JOIN tenant_membership m ON m.tenant_id = s.tenant_id AND m.user_id = ? AND m.role = 'OWNER'
 JOIN identity_user u ON u.id = m.user_id AND u.status = 'ACTIVE' AND u.verified_at IS NOT NULL
 WHERE s.tenant_id = ? FOR SHARE OF s, m, u
 """,
            (row, index) -> row.getObject("id", UUID.class),
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  /**
   * Acquire store write lock immediately; shared-lock upgrades can deadlock concurrent publishers.
   */
  public Optional<UUID> lockOwnedStoreForPublication(UUID actor, UUID tenant) {
    return jdbc
        .query(
            """
      SELECT s.id FROM store s
      JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=? AND m.role='OWNER'
      JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
      WHERE s.tenant_id=? FOR UPDATE OF s FOR SHARE OF m,u
      """,
            (row, index) -> row.getObject("id", UUID.class),
            actor,
            tenant)
        .stream()
        .findFirst();
  }
}
