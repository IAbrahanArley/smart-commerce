package com.smartcommerce.inventory.controllers;

import com.smartcommerce.inventory.domain.InventoryInput;
import com.smartcommerce.inventory.dto.request.InventoryMovementRequest;
import com.smartcommerce.inventory.dto.response.InventoryMutationView;
import com.smartcommerce.inventory.dto.response.InventoryView;
import com.smartcommerce.inventory.services.InventoryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/products/{productId}")
public final class InventoryController {
  private final InventoryService inventory;

  public InventoryController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @GetMapping("/inventory")
  public InventoryView find(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestParam(required = false) String before,
      Authentication actor) {
    return inventory.find(
        UUID.fromString(actor.getName()), tenantId, productId, InventoryInput.cursor(before));
  }

  @PostMapping("/inventory")
  public ResponseEntity<InventoryMutationView> move(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody InventoryMovementRequest request,
      Authentication actor) {
    var result =
        inventory.move(UUID.fromString(actor.getName()), tenantId, productId, request.values());
    return ResponseEntity.status(result.replayed() ? 200 : 201).body(result);
  }

  @GetMapping("/variants/{variantId}/inventory")
  public InventoryView findVariant(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @PathVariable UUID variantId,
      @RequestParam(required = false) String before,
      Authentication actor) {
    return inventory.findVariant(
        UUID.fromString(actor.getName()),
        tenantId,
        productId,
        variantId,
        InventoryInput.cursor(before));
  }

  @PostMapping("/variants/{variantId}/inventory")
  public ResponseEntity<InventoryMutationView> moveVariant(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @PathVariable UUID variantId,
      @RequestBody InventoryMovementRequest request,
      Authentication actor) {
    var result =
        inventory.moveVariant(
            UUID.fromString(actor.getName()), tenantId, productId, variantId, request.values());
    return ResponseEntity.status(result.replayed() ? 200 : 201).body(result);
  }
}
