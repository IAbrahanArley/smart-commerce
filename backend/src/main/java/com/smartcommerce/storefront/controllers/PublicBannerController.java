package com.smartcommerce.storefront.controllers;

import com.smartcommerce.content.controllers.HomeContentController;
import com.smartcommerce.storefront.services.PublicCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Images of the home banners a published store shows now (Sprint 8, SC-043). */
@Tag(name = "Storefront")
@RestController
public final class PublicBannerController {
  private final PublicCatalogService catalog;

  public PublicBannerController(PublicCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/api/public/stores/{slug}/banners/{banner}/images/{variant}")
  public ResponseEntity<byte[]> image(
      @PathVariable String slug, @PathVariable UUID banner, @PathVariable String variant) {
    return HomeContentController.content(
        catalog.bannerImage(slug, banner, HomeContentController.mobile(variant)));
  }
}
