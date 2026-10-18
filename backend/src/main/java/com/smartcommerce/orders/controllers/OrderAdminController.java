package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.AdminOrderQuery;
import com.smartcommerce.orders.domain.CancelReason;
import com.smartcommerce.orders.dto.request.CancelOrderRequest;
import com.smartcommerce.orders.dto.response.AdminOrderPage;
import com.smartcommerce.orders.dto.response.AdminOrderView;
import com.smartcommerce.orders.services.OrderAdminService;
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

  public OrderAdminController(OrderAdminService orders) {
    this.orders = orders;
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
}
