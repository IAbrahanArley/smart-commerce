package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.dto.request.ProductStorefrontRequest;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.services.ProductStorefrontService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The "Vitrine" card of a product: brand, address and "compare at" prices (SC-041). */
@SecurityRequirement(name = "session")
@Tag(name = "Catalog")
@RestController
public final class ProductStorefrontController {
  private final ProductStorefrontService storefront;

  public ProductStorefrontController(ProductStorefrontService storefront) {
    this.storefront = storefront;
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/products/{productId}/storefront")
  public ProductView update(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody ProductStorefrontRequest request,
      Authentication actor) {
    return storefront.update(UUID.fromString(actor.getName()), tenantId, productId, request);
  }
}
