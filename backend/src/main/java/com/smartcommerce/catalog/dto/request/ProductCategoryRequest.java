package com.smartcommerce.catalog.dto.request;

import java.util.UUID;

/** The product's new category; null removes it (MVP block 1c). */
public record ProductCategoryRequest(UUID categoryId) {}
