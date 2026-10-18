package com.smartcommerce.inventory.dto.response;

import java.util.List;

/**
 * {@code quantity} is the physical balance (unchanged contract); {@code reserved} counts ACTIVE,
 * unexpired reservations and {@code available} = max(0, quantity - reserved).
 */
public record InventoryView(
    int quantity, long reserved, int available, List<MovementView> movements, String nextCursor) {}
