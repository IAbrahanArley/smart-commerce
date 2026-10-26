package com.smartcommerce.catalog.domain;

import java.util.UUID;

/** A product category of the store and how many of its products use it (MVP block 1c). */
public record CategorySummary(UUID id, String name, long products) {}
