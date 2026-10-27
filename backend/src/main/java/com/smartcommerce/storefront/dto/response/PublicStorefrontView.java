package com.smartcommerce.storefront.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * One page of a published store. {@code categories} lists only categories with published products;
 * {@code category} is the filter applied to this page, or null for all products (MVP block 1c).
 */
public record PublicStorefrontView(
    String name,
    String slug,
    PublicStoreProfileView profile,
    List<PublicCategoryView> categories,
    UUID category,
    List<PublicListProductView> products,
    String nextCursor) {}
