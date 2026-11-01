package com.smartcommerce.tenant.dto.response;

import java.util.UUID;

/**
 * Authorized representation; identity is global while the role (OWNER, or STAFF since SC-049)
 * belongs to this tenant only. STAFF sees the company to reach its own features, nothing else.
 */
public record TenantView(UUID id, String companyName, StoreView store, String role) {
  public record StoreView(UUID id, String name, String slug, String status) {}
}
