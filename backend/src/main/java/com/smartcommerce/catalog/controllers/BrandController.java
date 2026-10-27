package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.domain.BrandSummary;
import com.smartcommerce.catalog.dto.request.BrandRequest;
import com.smartcommerce.catalog.services.BrandService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Brands of a store (Sprint 8, SC-041). */
@SecurityRequirement(name = "session")
@Tag(name = "Catalog")
@RestController
public final class BrandController {
  private final BrandService brands;

  public BrandController(BrandService brands) {
    this.brands = brands;
  }

  @GetMapping("/api/tenants/{tenantId}/brands")
  public List<BrandSummary> list(@PathVariable UUID tenantId, Authentication actor) {
    return brands.list(actor(actor), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/api/tenants/{tenantId}/brands")
  public ResponseEntity<List<BrandSummary>> create(
      @PathVariable UUID tenantId, @RequestBody BrandRequest request, Authentication actor) {
    return ResponseEntity.status(201).body(brands.create(actor(actor), tenantId, request.name()));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/brands/{brandId}")
  public List<BrandSummary> rename(
      @PathVariable UUID tenantId,
      @PathVariable UUID brandId,
      @RequestBody BrandRequest request,
      Authentication actor) {
    return brands.rename(actor(actor), tenantId, brandId, request.name());
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping("/api/tenants/{tenantId}/brands/{brandId}")
  public List<BrandSummary> delete(
      @PathVariable UUID tenantId, @PathVariable UUID brandId, Authentication actor) {
    return brands.delete(actor(actor), tenantId, brandId);
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }
}
