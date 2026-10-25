package com.smartcommerce.catalog.domain;

import java.util.UUID;

/** A product's cover (first photo by position) and how many photos it has. */
public record ProductImageSummary(UUID coverId, String coverAlt, int count) {
  public static final ProductImageSummary NONE = new ProductImageSummary(null, null, 0);
}
