package com.smartcommerce.catalog.exceptions;

/**
 * Category outcomes (MVP block 1c, tree in SC-041); the controller advice owns HTTP translation.
 */
public final class CategoryException extends RuntimeException {
  public enum Reason {
    MISSING,
    NAME_CONFLICT,
    SLUG_CONFLICT,
    LIMIT,
    /** Deeper than {@link com.smartcommerce.catalog.domain.CategoryTree#MAX_DEPTH} levels. */
    DEPTH,
    /** Moving a category under itself or one of its subcategories. */
    CYCLE,
    /** Removing a category that still has subcategories. */
    HAS_CHILDREN
  }

  private final Reason reason;

  public CategoryException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
