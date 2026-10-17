package com.smartcommerce.inventory.dto.response;

public record InventoryMutationView(MovementView movement, boolean replayed) {}
