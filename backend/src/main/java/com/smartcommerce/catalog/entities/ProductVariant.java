package com.smartcommerce.catalog.entities;

import com.smartcommerce.catalog.domain.VariantValues;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "catalog_variant")
public class ProductVariant {
  @Id private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "product_id")
  private UUID productId;

  private String sku;

  @Column(columnDefinition = "numeric")
  private BigDecimal price;

  @Column(columnDefinition = "numeric")
  private BigDecimal cost;

  private String currency;
  private long version;

  @Column(name = "options_json", columnDefinition = "text")
  private String optionsJson;

  @Column(name = "combination_key", columnDefinition = "text")
  private String combinationKey;

  protected ProductVariant() {}

  public ProductVariant(UUID id, UUID tenant, UUID product, VariantValues input) {
    this.id = id;
    tenantId = tenant;
    productId = product;
    currency = "BRL";
    optionsJson = input.json();
    combinationKey = input.key();
    sku = input.values().sku();
    price = input.values().price();
    cost = input.values().cost();
  }

  public UUID id() {
    return id;
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

  public String optionsJson() {
    return optionsJson;
  }
}
