package com.smartcommerce.storefront.dto.response;

import java.util.UUID;

/** A category with at least one published product (MVP block 1c). */
public record PublicCategoryView(UUID id, String name) {}
