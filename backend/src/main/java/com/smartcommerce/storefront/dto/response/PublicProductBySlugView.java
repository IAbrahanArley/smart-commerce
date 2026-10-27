package com.smartcommerce.storefront.dto.response;

/**
 * A product looked up by its storefront address (SC-042): either the product, or the address it
 * moved to (an old link still within 90 days), so the storefront can redirect.
 */
public record PublicProductBySlugView(String movedTo, PublicProductDetailsView product) {}
