package com.smartcommerce.catalog.dto.request;

import java.util.List;
import java.util.UUID;

/**
 * The categories a product also appears in besides its primary one (SC-043); the whole list is
 * replaced. At most {@code MAX} distinct ids.
 */
public record ProductCategoriesRequest(List<UUID> categoryIds) {
  public static final int MAX = 10;

  public List<UUID> value() {
    if (categoryIds == null
        || categoryIds.size() > MAX
        || categoryIds.stream().anyMatch(java.util.Objects::isNull)
        || categoryIds.stream().distinct().count() != categoryIds.size())
      throw new IllegalArgumentException("Invalid category list");
    return List.copyOf(categoryIds);
  }
}
