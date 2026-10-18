package com.smartcommerce.inventory.repositories;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.StockLevel;
import com.smartcommerce.inventory.entities.InventoryBalance;
import com.smartcommerce.inventory.entities.InventoryMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
  private final EntityManager entities;
  private final JdbcTemplate jdbc;

  public InventoryRepository(EntityManager entities, JdbcTemplate jdbc) {
    this.entities = entities;
    this.jdbc = jdbc;
  }

  /**
   * Lock order is request key, then product balance. Locks last until the service transaction ends.
   * Hash collisions only serialize unrelated requests; uniqueness remains enforced by PostgreSQL.
   */
  public InventoryBalance lock(UUID tenant, UUID product, UUID request) {
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        row -> {},
        tenant.toString() + ":" + request);
    jdbc.update(
        """
   INSERT INTO inventory_balance(product_id,tenant_id,quantity)
   SELECT p.id,p.tenant_id,0 FROM catalog_variant p WHERE p.id=? AND p.tenant_id=?
   ON CONFLICT(product_id) DO NOTHING
   """,
        product,
        tenant);
    return entities
        .createQuery(
            "select b from InventoryBalance b where b.tenantId=:tenant and b.productId=:product",
            InventoryBalance.class)
        .setParameter("tenant", tenant)
        .setParameter("product", product)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new InventoryException(InventoryException.Reason.MISSING));
  }

  /**
   * The single availability read: physical units, ACTIVE reservations whose {@code expires_at} is
   * after {@code now}, and available = max(0, physical - reserved), via the V11 SQL functions that
   * the storefront also uses. Only variants of {@code tenant} are returned, ordered by id.
   */
  public List<StockLevel> levels(UUID tenant, Collection<UUID> variants, Instant now) {
    var at = InventoryReservationRepository.utc(now);
    return jdbc.query(
        """
        SELECT v.id, COALESCE(b.quantity,0),
         inventory_reserved_quantity(v.tenant_id,v.id,?), inventory_available_quantity(v.tenant_id,v.id,?)
        FROM catalog_variant v
        LEFT JOIN inventory_balance b ON b.product_id=v.id AND b.tenant_id=v.tenant_id
        WHERE v.tenant_id=? AND v.id=ANY(CAST(? AS uuid[])) ORDER BY v.id
        """,
        (row, index) ->
            new StockLevel(
                row.getObject(1, UUID.class), row.getInt(2), row.getLong(3), row.getInt(4)),
        at,
        at,
        tenant,
        array(variants));
  }

  public StockLevel level(UUID tenant, UUID variant, Instant now) {
    return levels(tenant, List.of(variant), now).stream()
        .findFirst()
        .orElseThrow(() -> new InventoryException(InventoryException.Reason.MISSING));
  }

  /**
   * Locks the existing balances in ascending variant order (deadlock-free between overlapping
   * carts), then reads levels in a new statement. Under READ COMMITTED that statement sees every
   * reservation committed by the previous lock holder; callers must not use REPEATABLE READ.
   */
  public List<StockLevel> lockLevels(UUID tenant, Collection<UUID> variants, Instant now) {
    jdbc.queryForList(
        """
        SELECT b.product_id FROM inventory_balance b
        WHERE b.tenant_id=? AND b.product_id=ANY(CAST(? AS uuid[])) ORDER BY b.product_id FOR UPDATE
        """,
        UUID.class,
        tenant,
        array(variants));
    return levels(tenant, variants, now);
  }

  private static String array(Collection<UUID> ids) {
    var joiner = new StringJoiner(",", "{", "}");
    new TreeSet<>(ids).forEach(id -> joiner.add(id.toString()));
    return joiner.toString();
  }

  public Optional<InventoryMovement> previous(UUID tenant, UUID request) {
    return entities
        .createQuery(
            "select m from InventoryMovement m where m.tenantId=:tenant and m.requestId=:request",
            InventoryMovement.class)
        .setParameter("tenant", tenant)
        .setParameter("request", request)
        .getResultStream()
        .findFirst();
  }

  public List<InventoryMovement> history(UUID tenant, UUID product, Long before) {
    String query =
        "select m from InventoryMovement m where m.tenantId=:tenant and m.productId=:product"
            + (before == null ? "" : " and m.sequence<:before")
            + " order by m.sequence desc";
    var command =
        entities
            .createQuery(query, InventoryMovement.class)
            .setParameter("tenant", tenant)
            .setParameter("product", product)
            .setMaxResults(21);
    if (before != null) command.setParameter("before", before);
    return command.getResultList();
  }

  public void append(InventoryMovement movement) {
    entities.persist(movement);
    entities.flush();
  }
}
