package com.smartcommerce.inventory.dto.response;

import java.util.List;

public record InventoryView(int quantity, List<MovementView> movements, String nextCursor) {}
