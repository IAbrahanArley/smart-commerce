package com.smartcommerce.catalog.controllers;

import com.smartcommerce.catalog.domain.*;
import com.smartcommerce.catalog.dto.request.ProductImageOrderRequest;
import com.smartcommerce.catalog.dto.response.ProductImagesView;
import com.smartcommerce.catalog.services.ProductImageService;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductImageController {
  private final ProductImageService images;

  public ProductImageController(ProductImageService images) {
    this.images = images;
  }

  @GetMapping("/api/tenants/{tenant}/products/{product}/images")
  public ProductImagesView list(
      @PathVariable UUID tenant, @PathVariable UUID product, Authentication actor) {
    return images.list(UUID.fromString(actor.getName()), tenant, product);
  }

  @PostMapping(
      value = "/api/tenants/{tenant}/products/{product}/images",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ProductImagesView upload(
      @PathVariable UUID tenant,
      @PathVariable UUID product,
      Authentication actor,
      @RequestParam MultipartFile file,
      @RequestParam String alt,
      @RequestParam long version)
      throws IOException {
    if (file.getSize() > ImageNormalizer.MAX_BYTES)
      throw new ImageException(ImageException.Reason.TOO_LARGE, "Use uma imagem de até 5 MiB.");
    return images.upload(
        UUID.fromString(actor.getName()),
        tenant,
        product,
        version,
        alt,
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

  @PutMapping("/api/tenants/{tenant}/products/{product}/images/order")
  public ProductImagesView reorder(
      @PathVariable UUID tenant,
      @PathVariable UUID product,
      Authentication actor,
      @RequestBody ProductImageOrderRequest input) {
    if (input.version() == null)
      throw new ImageException(ImageException.Reason.INVALID, "Informe a versão atual do produto.");
    return images.reorder(
        UUID.fromString(actor.getName()), tenant, product, input.version(), input.imageIds());
  }

  @DeleteMapping("/api/tenants/{tenant}/products/{product}/images/{image}")
  public ProductImagesView remove(
      @PathVariable UUID tenant,
      @PathVariable UUID product,
      @PathVariable UUID image,
      Authentication actor,
      @RequestParam long version) {
    return images.remove(UUID.fromString(actor.getName()), tenant, product, image, version);
  }

  @GetMapping("/api/tenants/{tenant}/products/{product}/images/{image}/content")
  public ResponseEntity<byte[]> privateContent(
      @PathVariable UUID tenant,
      @PathVariable UUID product,
      @PathVariable UUID image,
      Authentication actor) {
    return content(images.privateContent(UUID.fromString(actor.getName()), tenant, product, image));
  }

  @GetMapping("/api/public/stores/{slug}/products/{product}/images")
  public Map<String, Object> publicList(@PathVariable String slug, @PathVariable UUID product) {
    return Map.of("images", images.publicList(slug, product));
  }

  @GetMapping("/api/public/stores/{slug}/products/{product}/images/{image}/content")
  public ResponseEntity<byte[]> publicContent(
      @PathVariable String slug, @PathVariable UUID product, @PathVariable UUID image) {
    return content(images.publicContent(slug, product, image));
  }

  @GetMapping("/api/public/stores/{slug}/products/{product}/images/primary/content")
  public ResponseEntity<byte[]> primary(@PathVariable String slug, @PathVariable UUID product) {
    return content(images.publicContent(slug, product, null));
  }

  private static ResponseEntity<byte[]> content(ImageContent data) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(data.mediaType()))
        .contentLength(data.bytes().length)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Disposition", "inline; filename=\"product-image\"")
        .body(data.bytes());
  }
}
