package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.AdminOrderQuery;
import com.smartcommerce.orders.domain.CancelReason;
import com.smartcommerce.orders.dto.request.CancelOrderRequest;
import com.smartcommerce.orders.dto.request.RefundOrderRequest;
import com.smartcommerce.orders.dto.response.AdminOrderPage;
import com.smartcommerce.orders.dto.response.AdminOrderView;
import com.smartcommerce.orders.services.OrderAdminService;
import com.smartcommerce.orders.services.OrderRefundService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Store OWNER orders (SC-010). Responses carry buyer data, so they are never cached. */
@SecurityRequirement(name = "session")
@Tag(name = "Orders")
@RestController
@RequestMapping("/api/tenants/{tenantId}/orders")
public final class OrderAdminController {
  private final OrderAdminService orders;
  private final OrderRefundService refunds;

  public OrderAdminController(OrderAdminService orders, OrderRefundService refunds) {
    this.orders = orders;
    this.refunds = refunds;
  }

  @GetMapping
  public ResponseEntity<AdminOrderPage> list(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String after,
      Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            orders.list(
                UUID.fromString(actor.getName()), tenantId, AdminOrderQuery.of(status, after)));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<AdminOrderView> find(
      @PathVariable UUID tenantId, @PathVariable UUID orderId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(orders.find(UUID.fromString(actor.getName()), tenantId, orderId));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/{orderId}/cancel")
  public ResponseEntity<AdminOrderView> cancel(
      @PathVariable UUID tenantId,
      @PathVariable UUID orderId,
      @RequestBody CancelOrderRequest request,
      Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            orders.cancel(
                UUID.fromString(actor.getName()),
                tenantId,
                orderId,
                CancelReason.of(request == null ? null : request.reason())));
  }

  /**
   * SC-016: full refund of a paid order. The body must state the reason and, explicitly, whether
   * the units go back to stock. Idempotent: a repeat returns the same refund.
   */
  @SecurityRequirement(name = "csrf")
  @PostMapping("/{orderId}/refund")
  public ResponseEntity<AdminOrderView> refund(
      @PathVariable UUID tenantId,
      @PathVariable UUID orderId,
      @RequestBody RefundOrderRequest request,
      Authentication actor) {
    if (request == null || request.restock() == null)
      throw new IllegalArgumentException("Restock choice is required");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            refunds.refund(
                UUID.fromString(actor.getName()),
                tenantId,
                orderId,
                CancelReason.of(request.reason()),
                request.restock()));
  }
}
