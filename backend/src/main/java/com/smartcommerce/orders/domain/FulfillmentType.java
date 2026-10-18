package com.smartcommerce.orders.domain;

/** Delivery choice of a checkout (ADR-0012 D3): store pickup or the store's single fixed fee. */
public enum FulfillmentType {
  PICKUP,
  FIXED_SHIPPING
}
