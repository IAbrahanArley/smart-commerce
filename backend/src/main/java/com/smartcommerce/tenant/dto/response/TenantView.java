package com.smartcommerce.tenant.dto.response;

import java.util.UUID;

/** Authorized representation; identity is global while OWNER belongs to this tenant only. */
public record TenantView(UUID id, String companyName, StoreView store, String role) {
  public record StoreView(UUID id, String name, String slug, String status) {}
}
