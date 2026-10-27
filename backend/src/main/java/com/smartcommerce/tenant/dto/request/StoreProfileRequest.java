package com.smartcommerce.tenant.dto.request;

import com.smartcommerce.tenant.domain.StoreProfileSettings;

/** Name and public contacts, with the version the form was loaded from (optimistic write). */
public record StoreProfileRequest(
    String name, String contactEmail, String whatsapp, String instagram, Long expectedVersion) {
  public StoreProfileSettings settings() {
    return StoreProfileSettings.of(name, contactEmail, whatsapp, instagram);
  }

  public long version() {
    if (expectedVersion == null) throw new IllegalArgumentException("Missing version");
    return expectedVersion;
  }
}
