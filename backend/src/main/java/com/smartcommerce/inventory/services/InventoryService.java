package com.smartcommerce.inventory.services;

import com.smartcommerce.catalog.services.CatalogService;
import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.InventoryInput;
import com.smartcommerce.inventory.domain.ReservationPolicy;
import com.smartcommerce.inventory.dto.response.*;
import com.smartcommerce.inventory.entities.InventoryMovement;
import com.smartcommerce.inventory.repositories.InventoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final CatalogService catalog;
  private final InventoryRepository inventory;
  private final Clock clock;

  public InventoryService(CatalogService catalog, InventoryRepository inventory, Clock clock) {
    this.catalog = catalog;
    this.inventory = inventory;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public InventoryView find(UUID actor, UUID tenant, UUID product, Long before) {
    catalog.requireStockAccess(actor, tenant, product, null);
    return read(tenant, product, before);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public InventoryView findVariant(
      UUID actor, UUID tenant, UUID product, UUID variant, Long before) {
    catalog.requireStockAccess(actor, tenant, product, variant);
    return read(tenant, variant, before);
  }

  private InventoryView read(UUID tenant, UUID product, Long before) {
    var level = inventory.level(tenant, product, ReservationPolicy.now(clock));
    var rows = inventory.history(tenant, product, before);
    var page = rows.stream().limit(20).map(InventoryService::view).toList();
    String cursor = rows.size() > 20 ? page.getLast().sequence() : null;
    return new InventoryView(level.onHand(), level.reserved(), level.available(), page, cursor);
  }

  // READ COMMITTED: the D7 check must see reservations committed while waiting for the lock.
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public InventoryMutationView move(UUID actor, UUID tenant, UUID product, InventoryInput input) {
    catalog.requireStockAccess(actor, tenant, product, null);
    return moveAuthorized(actor, tenant, product, input);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public InventoryMutationView moveVariant(
      UUID actor, UUID tenant, UUID product, UUID variant, InventoryInput input) {
    catalog.requireStockAccess(actor, tenant, product, variant);
    return moveAuthorized(actor, tenant, variant, input);
  }

  private InventoryMutationView moveAuthorized(
      UUID actor, UUID tenant, UUID product, InventoryInput input) {
    var balance = inventory.lock(tenant, product, input.requestId());
    var previous = inventory.previous(tenant, input.requestId());
    if (previous.isPresent()) {
      var movement = previous.get();
      if (!movement.matches(actor, product, input))
        throw new InventoryException(InventoryException.Reason.REQUEST_CONFLICT);
      return new InventoryMutationView(view(movement), true);
    }
    int next = input.balanceAfter(balance.quantity());
    // PostgreSQL stores microseconds: the first response and every replay must use the same
    // instant.
    Instant now = ReservationPolicy.now(clock);
    if (input.delta() < 0) {
      // D7: read under the balance lock, so a concurrent reservation is either visible or waits.
      long reserved = inventory.level(tenant, product, now).reserved();
      if (next < reserved)
        throw new InventoryException(InventoryException.Reason.RESERVED_STOCK, reserved);
    }
    var movement = new InventoryMovement(tenant, product, actor, input, next, now);
    balance.changeTo(next);
    inventory.append(movement);
    return new InventoryMutationView(view(movement), false);
  }

  private static MovementView view(InventoryMovement movement) {
    return new MovementView(
        movement.id(),
        movement.type().name(),
        movement.delta(),
        movement.balanceAfter(),
        movement.reason(),
        movement.createdAt(),
        movement.sequence().toString());
  }
}
