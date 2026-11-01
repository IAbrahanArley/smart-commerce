package com.smartcommerce.tenant.domain;

import java.time.Instant;
import java.util.UUID;

/** Persistence projections of store employees and their invitations (SC-049, ADR-0019 C1). */
public final class StaffRecords {
  private StaffRecords() {}

  /** An employee of the store: the e-mail is shown only to the store's OWNER. */
  public record Member(UUID userId, String email, Instant since) {}

  /** An invitation not yet accepted nor canceled; it may already be past its expiry. */
  public record OpenInvitation(UUID id, String email, Instant createdAt, Instant expiresAt) {}

  /** The invitation behind a token, locked for the acceptance transaction. */
  public record Redeemable(
      UUID id, UUID tenantId, String emailKey, Instant expiresAt, String storeName) {}
}
