package com.smartcommerce.storefront.domain;

/** Public absence is deliberately indistinguishable from unpublished or foreign resources. */
public final class StorefrontException extends RuntimeException {
  public StorefrontException() {
    super("Public storefront unavailable");
  }
}
