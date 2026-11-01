package com.smartcommerce.tenant.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The store's employees and open invitations, for its OWNER only (SC-049). {@code used} counts
 * people over all of the owner's companies, as the plan limit does; {@code enforced} is false while
 * billing is off.
 */
public record StaffOverview(
    List<Member> members, List<Invitation> invitations, long used, int limit, boolean enforced) {
  public record Member(UUID userId, String email, Instant since) {}

  public record Invitation(
      UUID id, String email, Instant createdAt, Instant expiresAt, boolean expired) {}
}
