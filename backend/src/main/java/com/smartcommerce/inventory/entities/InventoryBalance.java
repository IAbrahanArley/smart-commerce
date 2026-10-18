package com.smartcommerce.inventory.entities;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "inventory_balance")
public class InventoryBalance {
  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "quantity")
  private int quantity;

  protected InventoryBalance() {}

  public int quantity() {
    return quantity;
  }

  public void changeTo(int next) {
    if (next < 0) throw new IllegalArgumentException("Negative stock");
    quantity = next;
  }
}
