package com.smartcommerce.inventory.repositories;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.entities.InventoryBalance;
import com.smartcommerce.inventory.entities.InventoryMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

  public int quantity(UUID tenant, UUID product) {
    return entities
        .createQuery(
            "select b.quantity from InventoryBalance b where b.tenantId=:tenant and b.productId=:product",
            Integer.class)
        .setParameter("tenant", tenant)
        .setParameter("product", product)
        .getResultStream()
        .findFirst()
        .orElse(0);
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
