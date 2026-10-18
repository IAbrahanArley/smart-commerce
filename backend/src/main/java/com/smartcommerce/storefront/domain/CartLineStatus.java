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
   * comes from {@code CartQuoteRepository.SELLABLE_QUANTITY}; SC-005 replaces that expression with
   * the reservation-aware available quantity without changing this rule.
   */
  public static CartLineStatus of(long sellable, int requested) {
    if (sellable >= requested) return AVAILABLE;
    return sellable > 0 ? INSUFFICIENT : UNAVAILABLE;
  }
}
