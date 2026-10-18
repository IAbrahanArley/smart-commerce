package com.smartcommerce.inventory.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MovementView(
    UUID id,
    String type,
    int delta,
    int balanceAfter,
    String reason,
    Instant createdAt,
    String sequence) {}
