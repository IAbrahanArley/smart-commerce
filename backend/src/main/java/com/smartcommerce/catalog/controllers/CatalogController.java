package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.dto.request.ProductRequest;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.services.CatalogService;
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
@Tag(name = "Catalog")
@RestController
@RequestMapping("/api/tenants/{tenantId}/products")
public final class CatalogController {

  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public List<ProductView> list(@PathVariable UUID tenantId, Authentication actor) {
    return catalog.list(UUID.fromString(actor.getName()), tenantId);
  }

  @GetMapping("/{productId}")
  public ProductView find(
      @PathVariable UUID tenantId, @PathVariable UUID productId, Authentication actor) {
    return catalog.find(UUID.fromString(actor.getName()), tenantId, productId);
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping
  public ResponseEntity<ProductView> create(
      @PathVariable UUID tenantId, @RequestBody ProductRequest input, Authentication actor) {
    ProductView created =
        catalog.create(UUID.fromString(actor.getName()), tenantId, input.values());
    return ResponseEntity.created(
            URI.create("/api/tenants/" + tenantId + "/products/" + created.id()))
        .body(created);
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/{productId}")
  public ProductView update(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody ProductRequest input,
      Authentication actor) {
    return catalog.update(
        UUID.fromString(actor.getName()),
        tenantId,
        productId,
        input.values(),
        input.expectedVersion());
  }
}
