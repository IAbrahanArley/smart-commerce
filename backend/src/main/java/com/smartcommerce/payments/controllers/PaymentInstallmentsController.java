package com.smartcommerce.payments.controllers;

import com.smartcommerce.payments.dto.request.InstallmentsRequest;
import com.smartcommerce.payments.dto.response.InstallmentsView;
import com.smartcommerce.payments.services.PaymentConnectionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The most card installments the store offers in Checkout Pro (SC-042, ADR-0018 V4). */
@SecurityRequirement(name = "session")
@Tag(name = "Payments")
@RestController
@RequestMapping("/api/tenants/{tenantId}/payments/installments")
public final class PaymentInstallmentsController {
  private final PaymentConnectionService connections;

  public PaymentInstallmentsController(PaymentConnectionService connections) {
    this.connections = connections;
  }

  @GetMapping
  public ResponseEntity<InstallmentsView> get(@PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(connections.installments(UUID.fromString(actor.getName()), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping
  public ResponseEntity<InstallmentsView> put(
      @PathVariable UUID tenantId, @RequestBody InstallmentsRequest request, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            connections.installments(UUID.fromString(actor.getName()), tenantId, request.value()));
  }
}
