package com.smartcommerce.tenant.controllers;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.catalog.domain.ImageException;
import com.smartcommerce.catalog.domain.ImageNormalizer;
import com.smartcommerce.tenant.dto.request.StoreProfileRequest;
import com.smartcommerce.tenant.dto.response.StoreProfileView;
import com.smartcommerce.tenant.services.StoreProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Store name, public contacts and logo, edited by the OWNER (MVP block 1b). */
@SecurityRequirement(name = "session")
@Tag(name = "Tenant")
@RestController
@RequestMapping("/api/tenants/{tenantId}/profile")
public final class StoreProfileController {
  private final StoreProfileService profiles;

  public StoreProfileController(StoreProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  public ResponseEntity<StoreProfileView> find(@PathVariable UUID tenantId, Authentication actor) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(profiles.find(UUID.fromString(actor.getName()), tenantId));
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping
  public StoreProfileView update(
      @PathVariable UUID tenantId, @RequestBody StoreProfileRequest request, Authentication actor) {
    return profiles.update(
        UUID.fromString(actor.getName()), tenantId, request.settings(), request.version());
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public StoreProfileView uploadLogo(
      @PathVariable UUID tenantId,
      Authentication actor,
      @RequestParam MultipartFile file,
      @RequestParam long version) {
    if (file.getSize() > ImageNormalizer.MAX_BYTES)
      throw new ImageException(ImageException.Reason.TOO_LARGE, "Use uma imagem de até 5 MiB.");
    return profiles.uploadLogo(
        UUID.fromString(actor.getName()),
        tenantId,
        version,
        () -> {
          try {
            return file.getBytes();
          } catch (IOException exception) {
            throw new ImageException(
                ImageException.Reason.INVALID, "Não foi possível ler a imagem.");
          }
        },
        file.getOriginalFilename(),
        file.getContentType());
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping("/logo")
  public StoreProfileView removeLogo(
      @PathVariable UUID tenantId, Authentication actor, @RequestParam long version) {
    return profiles.removeLogo(UUID.fromString(actor.getName()), tenantId, version);
  }

  @GetMapping("/logo")
  public ResponseEntity<byte[]> logo(@PathVariable UUID tenantId, Authentication actor) {
    return image(profiles.logo(UUID.fromString(actor.getName()), tenantId));
  }

  static ResponseEntity<byte[]> image(ImageContent data) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(data.mediaType()))
        .contentLength(data.bytes().length)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Disposition", "inline; filename=\"store-logo\"")
        .body(data.bytes());
  }
}
