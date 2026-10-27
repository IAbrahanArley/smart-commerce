package com.smartcommerce.catalog.dto.request;

import java.util.UUID;

/**
 * A category name, validated by the service (1 to 60 characters). On creation, {@code parentId}
 * places it under another category (SC-041); renaming ignores it.
 */
public record CategoryRequest(String name, UUID parentId) {}
