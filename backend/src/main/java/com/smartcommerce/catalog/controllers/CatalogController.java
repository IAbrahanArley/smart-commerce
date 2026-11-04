package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.domain.ProductListQuery;
import com.smartcommerce.catalog.dto.request.ProductPublicationRequest;
import com.smartcommerce.catalog.dto.request.ProductRequest;
import com.smartcommerce.catalog.dto.request.VariantRequest;
import com.smartcommerce.catalog.dto.response.ProductPageView;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.services.CatalogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /** One page of the admin list (25 products), filtered, ordered and counted by the backend. */
  @GetMapping
  public ProductPageView list(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String visibility,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String page,
      Authentication actor) {
    return catalog.page(
        UUID.fromString(actor.getName()),
        tenantId,
        ProductListQuery.of(q, category, visibility, sort, page));
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
        catalog.create(UUID.fromString(actor.getName()), tenantId, input.values(), input.details());
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
        input.expectedVersion(),
        input.details());
  }

  @PostMapping("/{productId}/variants")
  public ProductView addVariant(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody VariantRequest input,
      Authentication actor) {
    return catalog.addVariant(
        UUID.fromString(actor.getName()),
        tenantId,
        productId,
        input.values("Variação"),
        input.expectedVersion());
  }

  @PutMapping("/{productId}/variants/{variantId}")
  public ProductView updateVariant(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @PathVariable UUID variantId,
      @RequestBody VariantRequest input,
      Authentication actor) {
    return catalog.updateVariant(
        UUID.fromString(actor.getName()),
        tenantId,
        productId,
        variantId,
        input.values("Variação"),
        input.expectedVersion());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/{productId}/publication")
  public ProductView publish(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody ProductPublicationRequest request,
      Authentication actor) {
    return catalog.publish(
        UUID.fromString(actor.getName()),
        tenantId,
        productId,
        request.value(),
        request.expectedVersion());
  }
}
