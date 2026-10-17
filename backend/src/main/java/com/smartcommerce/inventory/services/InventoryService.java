package com.smartcommerce.inventory.services;

import com.smartcommerce.catalog.services.CatalogService;
import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.InventoryInput;
import com.smartcommerce.inventory.dto.response.*;
import com.smartcommerce.inventory.entities.InventoryMovement;
import com.smartcommerce.inventory.repositories.InventoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final CatalogService catalog;
  private final InventoryRepository inventory;

  public InventoryService(CatalogService catalog, InventoryRepository inventory) {
    this.catalog = catalog;
    this.inventory = inventory;
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
    int quantity = inventory.quantity(tenant, product);
    var rows = inventory.history(tenant, product, before);
    var page = rows.stream().limit(20).map(InventoryService::view).toList();
    String cursor = rows.size() > 20 ? page.getLast().sequence() : null;
    return new InventoryView(quantity, page, cursor);
  }

  @Transactional
  public InventoryMutationView move(UUID actor, UUID tenant, UUID product, InventoryInput input) {
    catalog.requireStockAccess(actor, tenant, product, null);
    return moveAuthorized(actor, tenant, product, input);
  }

  @Transactional
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
    var movement =
        new InventoryMovement(
            tenant, product, actor, input, next, Instant.now().truncatedTo(ChronoUnit.MICROS));
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
