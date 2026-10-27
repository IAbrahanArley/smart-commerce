package com.smartcommerce.catalog.domain;

import java.util.UUID;

/** A brand of the store and how many of its products have it (Sprint 8, SC-041). */
public record BrandSummary(UUID id, String name, String slug, long products) {}
