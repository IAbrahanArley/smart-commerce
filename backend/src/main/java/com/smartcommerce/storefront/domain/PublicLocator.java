package com.smartcommerce.storefront.domain;

import java.util.UUID;

public final class PublicLocator {
  private PublicLocator() {}

  public static UUID uuid(String value) {
    if (value == null
        || !value.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new IllegalArgumentException("Invalid product cursor");
    return UUID.fromString(value);
  }

  public static String slug(String value) {
    if (value == null
        || value.length() < 3
        || value.length() > 63
        || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new StorefrontException();
    return value;
  }
}
