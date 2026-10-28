package com.smartcommerce.content.controllers;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.catalog.domain.ImageException;
import com.smartcommerce.catalog.domain.ImageNormalizer;
import com.smartcommerce.content.domain.BannerSummary;
import com.smartcommerce.content.domain.HomeSection;
import com.smartcommerce.content.dto.request.BannerOrderRequest;
import com.smartcommerce.content.dto.request.BannerRequest;
import com.smartcommerce.content.dto.request.SectionsRequest;
import com.smartcommerce.content.services.HomeContentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Banners and sections of a store home (Sprint 8, SC-043). */
@SecurityRequirement(name = "session")
@Tag(name = "Store content")
@RestController
@RequestMapping("/api/tenants/{tenantId}/content")
public final class HomeContentController {
  private final HomeContentService home;

  public HomeContentController(HomeContentService home) {
    this.home = home;
  }

  @GetMapping("/banners")
  public List<BannerSummary> banners(@PathVariable UUID tenantId, Authentication actor) {
    return home.banners(actor(actor), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping("/banners")
  public List<BannerSummary> create(
      @PathVariable UUID tenantId, @RequestBody BannerRequest request, Authentication actor) {
    return home.createBanner(actor(actor), tenantId, request.input());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/banners/order")
  public List<BannerSummary> reorder(
      @PathVariable UUID tenantId, @RequestBody BannerOrderRequest request, Authentication actor) {
    return home.reorder(actor(actor), tenantId, request.ids());
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/banners/{bannerId}")
  public List<BannerSummary> update(
      @PathVariable UUID tenantId,
      @PathVariable UUID bannerId,
      @RequestBody BannerRequest request,
      Authentication actor) {
    return home.updateBanner(actor(actor), tenantId, bannerId, request.input());
  }

  @SecurityRequirement(name = "csrf")
  @DeleteMapping("/banners/{bannerId}")
  public List<BannerSummary> delete(
      @PathVariable UUID tenantId, @PathVariable UUID bannerId, Authentication actor) {
    return home.deleteBanner(actor(actor), tenantId, bannerId);
  }

  @SecurityRequirement(name = "csrf")
  @PostMapping(
      value = "/banners/{bannerId}/images/{variant}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public List<BannerSummary> upload(
      @PathVariable UUID tenantId,
      @PathVariable UUID bannerId,
      @PathVariable String variant,
      @RequestParam MultipartFile file,
      Authentication actor) {
    if (file.getSize() > ImageNormalizer.MAX_BYTES)
      throw new ImageException(ImageException.Reason.TOO_LARGE, "Use uma imagem de até 5 MiB.");
    return home.uploadImage(
        actor(actor),
        tenantId,
        bannerId,
        mobile(variant),
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

  @GetMapping("/banners/{bannerId}/images/{variant}")
  public ResponseEntity<byte[]> image(
      @PathVariable UUID tenantId,
      @PathVariable UUID bannerId,
      @PathVariable String variant,
      Authentication actor) {
    return content(home.image(actor(actor), tenantId, bannerId, mobile(variant)));
  }

  @GetMapping("/sections")
  public List<HomeSection> sections(@PathVariable UUID tenantId, Authentication actor) {
    return home.sections(actor(actor), tenantId);
  }

  @SecurityRequirement(name = "csrf")
  @PutMapping("/sections")
  public List<HomeSection> saveSections(
      @PathVariable UUID tenantId, @RequestBody SectionsRequest request, Authentication actor) {
    return home.replaceSections(actor(actor), tenantId, request.list());
  }

  /** The two fixed image variants of a banner. */
  public static boolean mobile(String variant) {
    return switch (variant) {
      case "desktop" -> false;
      case "mobile" -> true;
      default -> throw new IllegalArgumentException("Image variant");
    };
  }

  public static ResponseEntity<byte[]> content(ImageContent data) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(data.mediaType()))
        .contentLength(data.bytes().length)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Disposition", "inline; filename=\"banner\"")
        .body(data.bytes());
  }

  private static UUID actor(Authentication actor) {
    return UUID.fromString(actor.getName());
  }
}
