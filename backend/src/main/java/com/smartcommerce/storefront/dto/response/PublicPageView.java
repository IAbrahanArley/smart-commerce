package com.smartcommerce.storefront.dto.response;

/** An institutional page of a published store (SC-043): sanitized product-description markup. */
public record PublicPageView(PublicStoreView store, String title, String slug, String bodyHtml) {}
