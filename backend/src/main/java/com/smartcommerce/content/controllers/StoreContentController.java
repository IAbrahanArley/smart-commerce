package com.smartcommerce.content.controllers;

import com.smartcommerce.content.domain.CompanyInfo;
import com.smartcommerce.content.domain.StorePageKind;
import com.smartcommerce.content.dto.request.CompanyRequest;
import com.smartcommerce.content.dto.request.PageRequest;
import com.smartcommerce.content.dto.response.PageView;
import com.smartcommerce.content.services.StoreContentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Company details and institutional pages of a store (Sprint 8, SC-043). */
@SecurityRequirement(name = "session")
@Tag(name = "Store content")
@RestController
@RequestMapping("/api/tenants/{tenantId}/content")
public final class StoreContentController {
  private final StoreContentService content;

  public StoreContentController(StoreContentService content) {
    this.content = content;
  }

  @GetMapping("/company")
  public CompanyInfo company(@PathVariable UUID tenantId, Authentication actor) {
    return content.company(actor(actor), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/company")
  public CompanyInfo saveCompany(
      @PathVariable UUID tenantId, @RequestBody CompanyRequest request, Authentication actor) {
    return content.saveCompany(actor(actor), tenantId, request.info());
  }

  @GetMapping("/pages")
  public List<PageView> pages(@PathVariable UUID tenantId, Authentication actor) {
    return views(content.pages(actor(actor), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/pages/{page}")
  public List<PageView> savePage(
      @PathVariable UUID tenantId,
      @PathVariable String page,
      @RequestBody PageRequest request,
      Authentication actor) {
    return views(
        content.savePage(actor(actor), tenantId, StorePageKind.fromSlug(page), request.bodyHtml()));
  }

  /** Every kind, in footer order, written or not. */
  private static List<PageView> views(Map<StorePageKind, String> pages) {
    return Arrays.stream(StorePageKind.values())
        .map(
            kind ->
                new PageView(kind.name(), kind.slug(), kind.title(), pages.getOrDefault(kind, "")))
        .toList();
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }
}
