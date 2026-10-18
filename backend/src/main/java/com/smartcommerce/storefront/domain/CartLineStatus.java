package com.smartcommerce.storefront.domain;

/**
 * Public line state. Absent, foreign and unpublished variants are all {@link #UNAVAILABLE}; the
 * exact quantity is never returned.
 */
public enum CartLineStatus {
  AVAILABLE,
  INSUFFICIENT,
  UNAVAILABLE;

  /**
   * Single comparison point between the requested and the sellable quantity. The sellable quantity
   * comes from {@code CartQuoteRepository.SELLABLE_QUANTITY}, the reservation-aware available
   * quantity (SC-005).
   */
  public static CartLineStatus of(long sellable, int requested) {
    if (sellable >= requested) return AVAILABLE;
    return sellable > 0 ? INSUFFICIENT : UNAVAILABLE;
  }
}
