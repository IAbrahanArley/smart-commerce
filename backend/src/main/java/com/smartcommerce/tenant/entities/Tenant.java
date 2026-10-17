package com.smartcommerce.tenant.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "company_name")
  private String companyName;

  @Column(name = "created_at")
  private Instant createdAt;

  protected Tenant() {}

  public Tenant(UUID id, String companyName, Instant createdAt) {
    this.id = id;
    this.companyName = companyName;
    this.createdAt = createdAt;
  }

  public UUID id() {
    return id;
  }

  public String companyName() {
    return companyName;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
