package com.smartcommerce.payments.controllers;

import com.smartcommerce.payments.dto.response.AuthorizationStartView;
import com.smartcommerce.payments.dto.response.PaymentConnectionView;
import com.smartcommerce.payments.services.PaymentConnectionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Store OWNER connection to Mercado Pago (SC-012). Responses never carry a token. */
@SecurityRequirement(name = "session")
@Tag(name = "Payments")
@RestController
@RequestMapping("/api/tenants/{tenantId}/payments/mercado-pago")
public final class PaymentConnectionController {
  private final PaymentConnectionService connections;

  public PaymentConnectionController(PaymentConnectionService connections) {
    this.connections = connections;
  }

  @GetMapping
  public ResponseEntity<PaymentConnectionView> status(
      @PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(connections.status(UUID.fromString(actor.getName()), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/authorization")
  public ResponseEntity<AuthorizationStartView> start(
      @PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(connections.startAuthorization(UUID.fromString(actor.getName()), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping
  public ResponseEntity<PaymentConnectionView> disconnect(
      @PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(connections.disconnect(UUID.fromString(actor.getName()), tenantId));
  }
}
