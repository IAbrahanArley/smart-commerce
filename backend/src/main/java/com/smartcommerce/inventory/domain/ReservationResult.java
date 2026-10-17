package com.smartcommerce.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResult(UUID orderId, Instant expiresAt, List<ReservationLine> lines) {}
