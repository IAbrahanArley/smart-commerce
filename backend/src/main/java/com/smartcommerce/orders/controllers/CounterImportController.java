package com.smartcommerce.orders.controllers;

import com.smartcommerce.orders.domain.CounterImportParser;
import com.smartcommerce.orders.dto.response.CounterImportViews;
import com.smartcommerce.orders.services.CounterImportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Counter sales from a spreadsheet (SC-054): the OWNER previews a CSV file, then confirms it. The
 * file travels as multipart, at most 1 MB; it is read in memory and never stored. Session and CSRF
 * on both; responses never cached.
 */
@SecurityRequirement(name = "session")
@SecurityRequirement(name = "csrf")
@Tag(name = "Orders")
@RestController
@RequestMapping("/api/tenants/{tenantId}/counter-sales/imports")
public final class CounterImportController {
  private final CounterImportService imports;

  public CounterImportController(CounterImportService imports) {
    this.imports = imports;
  }

  @PostMapping("/preview")
  public ResponseEntity<CounterImportViews.Preview> preview(
      @PathVariable UUID tenantId, @RequestParam MultipartFile file, Authentication actor) {
    return fresh(
        CounterImportViews.Preview.of(imports.preview(actor(actor), tenantId, bytes(file))));
  }

  @PostMapping
  public ResponseEntity<CounterImportViews.Result> confirm(
      @PathVariable UUID tenantId,
      @RequestParam MultipartFile file,
      @RequestParam(required = false) UUID cashAccountId,
      Authentication actor) {
    return fresh(
        CounterImportViews.Result.of(
            imports.confirm(actor(actor), tenantId, bytes(file), cashAccountId)));
  }

  /** At most one byte past the limit is read, so the parser refuses a larger file by its size. */
  private static byte[] bytes(MultipartFile file) {
    try (InputStream input = file.getInputStream()) {
      return input.readNBytes(CounterImportParser.MAX_BYTES + 1);
    } catch (IOException unreadable) {
      throw new IllegalArgumentException("Unreadable file");
    }
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }

  private static <T> ResponseEntity<T> fresh(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
