package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.domain.CategorySummary;
import com.smartcommerce.catalog.dto.request.CategoryPlacementRequest;
import com.smartcommerce.catalog.dto.request.CategoryPublicationRequest;
import com.smartcommerce.catalog.dto.request.CategoryRequest;
import com.smartcommerce.catalog.dto.request.CategorySlugRequest;
import com.smartcommerce.catalog.dto.request.ProductCategoriesRequest;
import com.smartcommerce.catalog.dto.request.ProductCategoryRequest;
import com.smartcommerce.catalog.dto.response.ProductView;
import com.smartcommerce.catalog.services.CategoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Product categories of a store and the category of each product (MVP block 1c); tree, slug, order
 * and visibility since Sprint 8 (SC-041).
 */
@SecurityRequirement(name = "session")
@Tag(name = "Catalog")
@RestController
public final class CategoryController {
  private final CategoryService categories;

  public CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  @GetMapping("/api/tenants/{tenantId}/categories")
  public List<CategorySummary> list(@PathVariable UUID tenantId, Authentication actor) {
    return categories.list(actor(actor), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/api/tenants/{tenantId}/categories")
  public ResponseEntity<List<CategorySummary>> create(
      @PathVariable UUID tenantId, @RequestBody CategoryRequest request, Authentication actor) {
    return ResponseEntity.status(201)
        .body(categories.create(actor(actor), tenantId, request.name(), request.parentId()));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/categories/{categoryId}/slug")
  public List<CategorySummary> changeSlug(
      @PathVariable UUID tenantId,
      @PathVariable UUID categoryId,
      @RequestBody CategorySlugRequest request,
      Authentication actor) {
    return categories.changeSlug(actor(actor), tenantId, categoryId, request.slug());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/categories/{categoryId}/placement")
  public List<CategorySummary> place(
      @PathVariable UUID tenantId,
      @PathVariable UUID categoryId,
      @RequestBody CategoryPlacementRequest request,
      Authentication actor) {
    return categories.place(
        actor(actor), tenantId, categoryId, request.parentId(), request.value());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/categories/{categoryId}/publication")
  public List<CategorySummary> publish(
      @PathVariable UUID tenantId,
      @PathVariable UUID categoryId,
      @RequestBody CategoryPublicationRequest request,
      Authentication actor) {
    return categories.publish(actor(actor), tenantId, categoryId, request.value());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/categories/{categoryId}")
  public List<CategorySummary> rename(
      @PathVariable UUID tenantId,
      @PathVariable UUID categoryId,
      @RequestBody CategoryRequest request,
      Authentication actor) {
    return categories.rename(actor(actor), tenantId, categoryId, request.name());
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping("/api/tenants/{tenantId}/categories/{categoryId}")
  public List<CategorySummary> delete(
      @PathVariable UUID tenantId, @PathVariable UUID categoryId, Authentication actor) {
    return categories.delete(actor(actor), tenantId, categoryId);
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/products/{productId}/category")
  public ProductView assign(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody ProductCategoryRequest request,
      Authentication actor) {
    return categories.assign(actor(actor), tenantId, productId, request.categoryId());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/api/tenants/{tenantId}/products/{productId}/categories")
  public ProductView assignAdditional(
      @PathVariable UUID tenantId,
      @PathVariable UUID productId,
      @RequestBody ProductCategoriesRequest request,
      Authentication actor) {
    return categories.assignAdditional(actor(actor), tenantId, productId, request.value());
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }
}
