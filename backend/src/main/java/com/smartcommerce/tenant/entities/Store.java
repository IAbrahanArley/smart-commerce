package com.smartcommerce.tenant.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store")
public class Store {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "name")
  private String name;

  @Column(name = "slug")
  private String slug;

  @Column(name = "status")
  private String status;

  @Column(name = "created_at")
  private Instant createdAt;

  protected Store() {}

  public Store(UUID id, UUID tenantId, String name, String slug, String status, Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.slug = slug;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public String name() {
    return name;
  }

  public String slug() {
    return slug;
  }

  public String status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
