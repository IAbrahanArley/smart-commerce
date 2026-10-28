package com.smartcommerce.content.domain;

/** The store already has as many banners as allowed (SC-043): 409 LIMIT. */
public final class ContentLimitException extends RuntimeException {
  public ContentLimitException() {
    super("Banner limit reached");
  }
}
