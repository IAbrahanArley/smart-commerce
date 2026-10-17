package com.smartcommerce.tenant.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_membership")
@IdClass(MembershipId.class)
public class TenantMembership {
  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "role")
  private String role;

  @Column(name = "created_at")
  private Instant createdAt;

  protected TenantMembership() {}

  public TenantMembership(UUID tenantId, UUID userId, Instant createdAt) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.createdAt = createdAt;
    this.role = "OWNER";
  }
}
