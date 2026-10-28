package com.smartcommerce.storefront.controllers;

import com.smartcommerce.storefront.domain.CatalogQuery;
import com.smartcommerce.storefront.dto.response.PublicCatalogView;
import com.smartcommerce.storefront.services.PublicCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The storefront catalog with search, filters, facets, sort and numbered pages (Sprint 8, SC-042).
 * Parameters keep the storefront's Portuguese names so a page URL can carry them as they are:
 * {@code categoria}, {@code marca} (repeatable or comma-separated), {@code preco_min}, {@code
 * preco_max}, {@code opcao} ("Tamanho:M", repeatable), {@code q}, {@code ordem} and {@code pagina}.
 */
@RestController
@Tag(name = "Public storefront")
@RequestMapping("/api/public/stores/{slug}/catalog")
public final class PublicCatalogController {
  private final PublicCatalogService catalog;

  public PublicCatalogController(PublicCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public ResponseEntity<PublicCatalogView> catalog(
      @PathVariable String slug,
      @RequestParam(required = false) String categoria,
      @RequestParam(required = false) List<String> marca,
      @RequestParam(name = "preco_min", required = false) String precoMin,
      @RequestParam(name = "preco_max", required = false) String precoMax,
      @RequestParam(required = false) List<String> opcao,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String ordem,
      @RequestParam(required = false) String pagina,
      @RequestParam(required = false) String destaque) {
    CatalogQuery query =
        CatalogQuery.parse(categoria, marca, precoMin, precoMax, opcao, q, ordem, pagina, destaque);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(catalog.catalog(slug, query));
  }
}
