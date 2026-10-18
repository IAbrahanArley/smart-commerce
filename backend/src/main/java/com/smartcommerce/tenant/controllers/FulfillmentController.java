package com.smartcommerce.tenant.controllers;

import com.smartcommerce.tenant.dto.request.FulfillmentRequest;
import com.smartcommerce.tenant.dto.response.FulfillmentView;
import com.smartcommerce.tenant.services.FulfillmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "session")
@Tag(name = "Tenant")
@RestController
@RequestMapping("/api/tenants/{tenantId}/fulfillment")
public final class FulfillmentController {
  private final FulfillmentService fulfillment;

  public FulfillmentController(FulfillmentService fulfillment) {
    this.fulfillment = fulfillment;
  }

  @GetMapping
  public FulfillmentView find(@PathVariable UUID tenantId, Authentication actor) {
    return fulfillment.find(UUID.fromString(actor.getName()), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping
  public FulfillmentView update(
      @PathVariable UUID tenantId, @RequestBody FulfillmentRequest request, Authentication actor) {
    return fulfillment.update(
        UUID.fromString(actor.getName()), tenantId, request.settings(), request.expectedVersion());
  }
}
