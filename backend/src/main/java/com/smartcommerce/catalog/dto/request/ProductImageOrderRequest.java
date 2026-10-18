package com.smartcommerce.catalog.dto.request;

import java.util.List;
import java.util.UUID;

public record ProductImageOrderRequest(List<UUID> imageIds, Long version) {}
