package com.smartcommerce.content.dto.request;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Every banner of the store, in the order the home shows them. */
public record BannerOrderRequest(List<UUID> bannerIds) {
  public List<UUID> ids() {
    if (bannerIds == null || bannerIds.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid banner order");
    return bannerIds;
  }
}
