package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.domain.PublicLocator;
import com.smartcommerce.storefront.dto.response.PublicProductDetailsView;
import com.smartcommerce.storefront.dto.response.PublicStorefrontView;
import com.smartcommerce.storefront.services.StorefrontService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Public storefront")
@RequestMapping("/api/public/stores/{slug}")
public final class StorefrontController {
  private final StorefrontService storefront;

  public StorefrontController(StorefrontService storefront) {
    this.storefront = storefront;
  }

  @GetMapping
  public ResponseEntity<PublicStorefrontView> list(
      @PathVariable String slug,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) String categoria) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            storefront.list(
                slug,
                after == null ? null : PublicLocator.uuid(after),
                categoria == null ? null : PublicLocator.uuid(categoria)));
  }

  /** The store logo (MVP block 1b), only while the store is published. */
  @GetMapping("/logo")
  public ResponseEntity<byte[]> logo(@PathVariable String slug) {
    var logo = storefront.logo(slug);
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.parseMediaType(logo.mediaType()))
        .contentLength(logo.bytes().length)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Disposition", "inline; filename=\"store-logo\"")
        .body(logo.bytes());
  }

  @GetMapping("/products/{productId}")
  public ResponseEntity<PublicProductDetailsView> find(
      @PathVariable String slug, @PathVariable String productId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(storefront.find(slug, PublicLocator.uuid(productId)));
  }
}
