package com.smartcommerce.inventory.entities;

import com.smartcommerce.inventory.domain.InventoryInput;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "inventory_movement")
public class InventoryMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sequence")
  private Long sequence;

  @Column(name = "id", updatable = false)
  private UUID id;

  @Column(name = "tenant_id", updatable = false)
  private UUID tenantId;

  @Column(name = "product_id", updatable = false)
  private UUID productId;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(name = "request_id", updatable = false)
  private UUID requestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", updatable = false)
  private InventoryInput.MovementType type;

  @Column(name = "delta", updatable = false)
  private int delta;

  @Column(name = "balance_after", updatable = false)
  private int balanceAfter;

  @Column(name = "reason", updatable = false)
  private String reason;

  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  protected InventoryMovement() {}

  public InventoryMovement(
      UUID tenantId,
      UUID productId,
      UUID actorId,
      InventoryInput input,
      int balanceAfter,
      Instant now) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.productId = productId;
    this.actorId = actorId;
    this.requestId = input.requestId();
    this.type = input.type();
    this.delta = input.delta();
    this.reason = input.reason();
    this.balanceAfter = balanceAfter;
    this.createdAt = now;
  }

  public boolean matches(UUID actor, UUID product, InventoryInput input) {
    return actorId.equals(actor)
        && productId.equals(product)
        && type == input.type()
        && delta == input.delta()
        && reason.equals(input.reason());
  }

  public Long sequence() {
    return sequence;
  }

  public UUID id() {
    return id;
  }

  public InventoryInput.MovementType type() {
    return type;
  }

  public int delta() {
    return delta;
  }

  public int balanceAfter() {
    return balanceAfter;
  }

  public String reason() {
    return reason;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
