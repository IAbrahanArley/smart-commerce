package com.smartcommerce.tenant.controllers;

import com.smartcommerce.tenant.dto.request.StorePublicationRequest;
import com.smartcommerce.tenant.dto.request.TenantCreationRequest;
import com.smartcommerce.tenant.dto.response.TenantView;
import com.smartcommerce.tenant.services.TenantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "session")
@Tag(name = "Tenant")
@RestController
@RequestMapping("/api/tenants")
public final class TenantController {

  private final TenantService tenants;

  public TenantController(TenantService tenants) {
    this.tenants = tenants;
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping
  public ResponseEntity<TenantView> create(
      @RequestBody TenantCreationRequest request, Authentication actor) {
    TenantView created =
        tenants.create(
            UUID.fromString(actor.getName()),
            request.companyName(),
            request.storeName(),
            request.slug());
    return ResponseEntity.created(URI.create("/api/tenants/" + created.id())).body(created);
  }

  @GetMapping
  public List<TenantView> list(Authentication actor) {
    return tenants.list(UUID.fromString(actor.getName()));
  }

  @GetMapping("/{tenantId}")
  public TenantView find(@PathVariable UUID tenantId, Authentication actor) {
    return tenants.find(UUID.fromString(actor.getName()), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/{tenantId}/publication")
  public TenantView publish(
      @PathVariable UUID tenantId,
      @RequestBody StorePublicationRequest request,
      Authentication actor) {
    return tenants.publish(UUID.fromString(actor.getName()), tenantId, request.value());
  }
}
