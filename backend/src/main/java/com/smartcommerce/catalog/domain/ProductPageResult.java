package com.smartcommerce.catalog.domain;

import java.util.List;
import java.util.UUID;

/**
 * One page of product ids in list order, how many products match the filters and how many the store
 * has in all and selected for the storefront.
 */
public record ProductPageResult(List<UUID> ids, long matching, long catalog, long published) {}
