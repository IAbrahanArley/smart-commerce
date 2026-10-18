package com.smartcommerce.catalog.domain;

import java.util.UUID;

public record ProductImageData(UUID id, String alt, int width, int height) {}
