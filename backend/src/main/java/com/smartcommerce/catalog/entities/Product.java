package com.smartcommerce.catalog.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_product")
public class Product {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "store_id")
  private UUID storeId;

  @Column(name = "name")
  private String name;

  @Column(name = "sku")
  private String sku;

  @Column(name = "price", columnDefinition = "numeric")
  private BigDecimal price;

  @Column(name = "cost", columnDefinition = "numeric")
  private BigDecimal cost;

  @Column(name = "currency")
  private String currency;

  @Column(name = "version")
  private long version;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "published")
  private boolean published;

  @Column(name = "description_html", columnDefinition = "text")
  private String descriptionHtml = "";

  @Column(name = "kind")
  private String kind = "SIMPLE";

  public String descriptionHtml() {
    return descriptionHtml;
  }

  public String kind() {
    return kind;
  }

  public void initializeDetails(String description, String kind) {
    this.descriptionHtml = description;
    this.kind = kind;
  }

  protected Product() {}

  public Product(
      UUID id,
      UUID tenantId,
      UUID storeId,
      String name,
      String sku,
      BigDecimal price,
      BigDecimal cost,
      String currency,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.storeId = storeId;
    this.name = name;
    this.sku = sku;
    this.price = price;
    this.cost = cost;
    this.currency = currency;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public boolean published() {
    return published;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID storeId() {
    return storeId;
  }

  public String name() {
    return name;
  }

  public String sku() {
    return sku;
  }

  public BigDecimal price() {
    return price;
  }

  public BigDecimal cost() {
    return cost;
  }

  public String currency() {
    return currency;
  }

  public long version() {
    return version;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
