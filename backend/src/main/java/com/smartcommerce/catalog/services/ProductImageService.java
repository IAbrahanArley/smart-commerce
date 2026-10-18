package com.smartcommerce.catalog.services;

import com.smartcommerce.catalog.domain.*;
import com.smartcommerce.catalog.dto.response.ProductImagesView;
import com.smartcommerce.catalog.repositories.ProductImageRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImageService {
  private final CatalogService catalog;
  private final ProductImageRepository images;
  private final ImageNormalizer normalizer;

  public ProductImageService(
      CatalogService catalog, ProductImageRepository images, ImageNormalizer normalizer) {
    this.catalog = catalog;
    this.images = images;
    this.normalizer = normalizer;
  }

  @Transactional
  public ProductImagesView list(UUID actor, UUID tenant, UUID product) {
    catalog.requireProductAccess(actor, tenant, product);
    long version = images.lock(tenant, product);
    return new ProductImagesView(images.list(tenant, product), version);
  }

  @Transactional
  public ProductImagesView upload(
      UUID actor,
      UUID tenant,
      UUID product,
      long version,
      String alt,
      java.util.function.Supplier<byte[]> bytes,
      String filename,
      String type) {
    authorizeVersion(actor, tenant, product, version);
    var current = images.list(tenant, product);
    if (current.size() >= 6)
      throw new ImageException(
          ImageException.Reason.IMAGE_LIMIT,
          "O produto já possui seis imagens. Remova uma antes de enviar outra.");
    String label = alt == null ? "" : alt.strip();
    if (label.isEmpty()
        || label.codePointCount(0, label.length()) > 200
        || label.codePoints().anyMatch(Character::isISOControl))
      throw new ImageException(
          ImageException.Reason.INVALID,
          "Descreva a imagem com até 200 caracteres, sem quebras de linha.");
    var content = normalizer.normalize(bytes.get(), filename, type);
    images.insert(UUID.randomUUID(), tenant, product, label, current.size(), content);
    images.bump(tenant, product, version);
    return new ProductImagesView(images.list(tenant, product), version + 1);
  }

  @Transactional
  public ProductImagesView reorder(
      UUID actor, UUID tenant, UUID product, long version, List<UUID> order) {
    authorizeVersion(actor, tenant, product, version);
    var existing = images.list(tenant, product);
    if (order == null
        || order.size() > 6
        || order.size() != existing.size()
        || new HashSet<>(order).size() != order.size()
        || !new HashSet<>(order)
            .equals(new HashSet<>(existing.stream().map(ProductImageData::id).toList())))
      throw new ImageException(
          ImageException.Reason.INVALID,
          "A ordem deve conter todas as imagens do produto, sem repetições.");
    for (int i = 0; i < order.size(); i++) images.position(tenant, product, order.get(i), i);
    images.bump(tenant, product, version);
    return new ProductImagesView(images.list(tenant, product), version + 1);
  }

  @Transactional
  public ProductImagesView remove(UUID actor, UUID tenant, UUID product, UUID image, long version) {
    authorizeVersion(actor, tenant, product, version);
    images.remove(tenant, product, image);
    var current = images.list(tenant, product);
    for (int i = 0; i < current.size(); i++)
      images.position(tenant, product, current.get(i).id(), i);
    images.bump(tenant, product, version);
    return new ProductImagesView(images.list(tenant, product), version + 1);
  }

  @Transactional
  public ImageContent privateContent(UUID actor, UUID tenant, UUID product, UUID image) {
    catalog.requireProductAccess(actor, tenant, product);
    return images.content(tenant, product, image);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<ProductImageData> publicList(String slug, UUID product) {
    return images.list(images.publishedTenant(slug, product), product);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ImageContent publicContent(String slug, UUID product, UUID image) {
    UUID tenant = images.publishedTenant(slug, product);
    UUID selected = image;
    if (selected == null)
      selected =
          images.list(tenant, product).stream()
              .findFirst()
              .orElseThrow(
                  () -> new ImageException(ImageException.Reason.MISSING, "Imagem indisponível."))
              .id();
    return images.content(tenant, product, selected);
  }

  private void authorizeVersion(UUID actor, UUID tenant, UUID product, long version) {
    catalog.requireProductAccess(actor, tenant, product);
    long actual = images.lock(tenant, product);
    if (version < 0 || version == Long.MAX_VALUE || actual != version)
      throw new ImageException(
          ImageException.Reason.VERSION_CONFLICT,
          "O produto foi alterado. Carregue a versão atual.");
  }
}
