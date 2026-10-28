package com.smartcommerce.content.services;

import com.smartcommerce.catalog.domain.ProductDescription;
import com.smartcommerce.content.domain.CompanyInfo;
import com.smartcommerce.content.domain.ContentException;
import com.smartcommerce.content.domain.StorePageKind;
import com.smartcommerce.content.repositories.StoreContentRepository;
import com.smartcommerce.tenant.repositories.TenantAccessRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company details and institutional pages of a store (Sprint 8, SC-043, ADR-0018 V8). Only the
 * store OWNER reads or writes them; another tenant's store is the same 404 as none. Page bodies
 * pass the product description sanitizer: paragraphs, line breaks, bold, italic and lists only.
 */
@Service
public class StoreContentService {
  private final TenantAccessRepository access;
  private final StoreContentRepository content;

  public StoreContentService(TenantAccessRepository access, StoreContentRepository content) {
    this.access = access;
    this.content = content;
  }

  @Transactional
  public CompanyInfo company(UUID actor, UUID tenant) {
    store(actor, tenant);
    return content.company(tenant).orElse(CompanyInfo.EMPTY);
  }

  @Transactional
  public CompanyInfo saveCompany(UUID actor, UUID tenant, CompanyInfo info) {
    content.saveCompany(store(actor, tenant), tenant, info, Instant.now());
    return content.company(tenant).orElse(CompanyInfo.EMPTY);
  }

  @Transactional
  public Map<StorePageKind, String> pages(UUID actor, UUID tenant) {
    store(actor, tenant);
    return content.pages(tenant);
  }

  /** Sanitizes and writes the page; an empty body (after sanitizing) removes it. */
  @Transactional
  public Map<StorePageKind, String> savePage(
      UUID actor, UUID tenant, StorePageKind kind, String rawHtml) {
    String html = ProductDescription.sanitize(rawHtml == null ? "" : rawHtml).strip();
    // Markup without text ("<p></p>") is an empty page too.
    if (html.replaceAll("<[^>]*>", "").isBlank()) html = "";
    content.savePage(store(actor, tenant), tenant, kind, html, Instant.now());
    return content.pages(tenant);
  }

  private UUID store(UUID actor, UUID tenant) {
    return access.lockOwnedStore(actor, tenant).orElseThrow(ContentException::new);
  }
}
