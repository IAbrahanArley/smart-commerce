package com.smartcommerce.catalog.dto.response;

import java.util.List;

/**
 * One page of the admin product list: {@code matching} products fit the filters, the store has
 * {@code catalogTotal} in all and {@code publishedTotal} selected for the storefront.
 */
public record ProductPageView(
    List<ProductView> products,
    int page,
    int pageSize,
    long matching,
    long catalogTotal,
    long publishedTotal) {}
