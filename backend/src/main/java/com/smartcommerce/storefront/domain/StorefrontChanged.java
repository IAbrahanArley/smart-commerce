package com.smartcommerce.storefront.domain;

import java.util.UUID;

/**
 * Something the storefront of this tenant shows has changed (SC-047, ADR-0018 V9): a product,
 * photo, category, brand, page, banner, section, store detail or the store status. Published inside
 * the transaction that made the change and handled only after it commits.
 */
public record StorefrontChanged(UUID tenant) {}
