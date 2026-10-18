package com.smartcommerce.inventory.repositories;

import com.smartcommerce.inventory.domain.ReservationLine;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reservation rows only. Callers hold the ordered balance locks of {@link
 * InventoryRepository#lockLevels} before inserting or releasing, so availability cannot change
 * between the check and the write. Every statement is scoped by tenant except the cross-tenant
 * expiry batch, which keeps each row's own tenant.
 */
@Repository
public class InventoryReservationRepository {
  private final JdbcTemplate jdbc;

  public InventoryReservationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean exists(UUID tenant, UUID order) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM inventory_reservation WHERE tenant_id=? AND order_id=?)",
            Boolean.class,
            tenant,
            order));
  }

  public void insert(
      UUID tenant, UUID order, List<ReservationLine> lines, Instant createdAt, Instant expiresAt) {
    jdbc.batchUpdate(
        """
        INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at)
        VALUES (?,?,?,?,?,'ACTIVE',?,?)
        """,
        lines,
        lines.size(),
        (statement, line) -> {
          statement.setObject(1, UUID.randomUUID());
          statement.setObject(2, tenant);
          statement.setObject(3, line.variantId());
          statement.setObject(4, order);
          statement.setInt(5, line.quantity());
          statement.setObject(6, utc(expiresAt));
          statement.setObject(7, utc(createdAt));
        });
  }

  public List<UUID> activeVariants(UUID tenant, UUID order) {
    return jdbc.queryForList(
        "SELECT variant_id FROM inventory_reservation WHERE tenant_id=? AND order_id=? AND status='ACTIVE' ORDER BY variant_id",
        UUID.class,
        tenant,
        order);
  }

  /** Closes ACTIVE rows once; rows already past their deadline are recorded as EXPIRED. */
  public int release(UUID tenant, UUID order, Instant now) {
    return jdbc.update(
        """
        UPDATE inventory_reservation
        SET status=CASE WHEN expires_at>? THEN 'RELEASED' ELSE 'EXPIRED' END, closed_at=?
        WHERE tenant_id=? AND order_id=? AND status='ACTIVE'
        """,
        utc(now),
        utc(now),
        tenant,
        order);
  }

  /**
   * Materializes logically expired rows. Expired rows no longer count toward availability, so no
   * balance lock is needed; SKIP LOCKED lets concurrent runs split the work without blocking.
   */
  public int expireDue(Instant now, int limit) {
    return jdbc.update(
        """
        UPDATE inventory_reservation SET status='EXPIRED', closed_at=?
        WHERE id IN (SELECT id FROM inventory_reservation WHERE status='ACTIVE' AND expires_at<=?
          ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED)
        AND status='ACTIVE'
        """,
        utc(now),
        utc(now),
        limit);
  }

  static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
