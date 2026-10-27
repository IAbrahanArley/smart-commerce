package com.smartcommerce.catalog.domain;

import java.util.UUID;

/**
 * A product category of the store and how many products have it as their category (MVP block 1c),
 * placed in the tree of up to 3 levels (Sprint 8, SC-041): {@code depth} 1 is a root, {@code
 * position} orders siblings, and a hidden ({@code published} false) category is left out of the
 * storefront without hiding its products.
 */
public record CategorySummary(
    UUID id,
    String name,
    String slug,
    UUID parentId,
    int depth,
    int position,
    boolean published,
    long products) {}
