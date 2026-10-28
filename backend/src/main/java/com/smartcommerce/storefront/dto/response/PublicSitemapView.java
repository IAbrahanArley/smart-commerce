package com.smartcommerce.storefront.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Content of a store sitemap (SC-047): product addresses with their last change, category and page
 * addresses. The storefront builds the absolute URLs.
 */
public record PublicSitemapView(
    List<Product> products, List<String> categories, List<String> pages) {
  public record Product(String slug, Instant updatedAt) {}
}
