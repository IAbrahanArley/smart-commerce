package com.smartcommerce.content.services;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.catalog.domain.ImageNormalizer;
import com.smartcommerce.content.domain.BannerInput;
import com.smartcommerce.content.domain.BannerSummary;
import com.smartcommerce.content.domain.ContentException;
import com.smartcommerce.content.domain.ContentLimitException;
import com.smartcommerce.content.domain.HomeSection;
import com.smartcommerce.content.repositories.HomeContentRepository;
import com.smartcommerce.storefront.services.StorefrontChanges;
import com.smartcommerce.tenant.repositories.TenantAccessRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Banners and sections of a store home (Sprint 8, SC-043, ADR-0018 V8). Only the store OWNER; a
 * banner, category or store of another tenant is the same 404 as none. Banner images go through the
 * product photo normalizer (re-encoded JPEG/PNG, metadata dropped, at least 300 px).
 */
@Service
public class HomeContentService {
  public static final int MAX_BANNERS = 5;

  private final TenantAccessRepository access;
  private final HomeContentRepository home;
  private final ImageNormalizer normalizer;

  private final StorefrontChanges changes;

  public HomeContentService(
      TenantAccessRepository access,
      HomeContentRepository home,
      ImageNormalizer normalizer,
      StorefrontChanges changes) {
    this.changes = changes;
    this.access = access;
    this.home = home;
    this.normalizer = normalizer;
  }

  @Transactional
  public List<BannerSummary> banners(UUID actor, UUID tenant) {
    store(actor, tenant);
    return home.banners(tenant);
  }

  /** A new banner goes last; it shows in the storefront once it has a desktop image. */
  @Transactional
  public List<BannerSummary> createBanner(UUID actor, UUID tenant, BannerInput input) {
    changes.changed(tenant);
    UUID store = store(actor, tenant);
    int count = home.lockBanners(tenant);
    if (count >= MAX_BANNERS) throw new ContentLimitException();
    home.createBanner(UUID.randomUUID(), store, tenant, count, input, Instant.now());
    return home.banners(tenant);
  }

  @Transactional
  public List<BannerSummary> updateBanner(UUID actor, UUID tenant, UUID banner, BannerInput input) {
    changes.changed(tenant);
    store(actor, tenant);
    if (!home.updateBanner(tenant, banner, input)) throw new ContentException();
    return home.banners(tenant);
  }

  @Transactional
  public List<BannerSummary> deleteBanner(UUID actor, UUID tenant, UUID banner) {
    changes.changed(tenant);
    store(actor, tenant);
    home.lockBanners(tenant);
    if (!home.deleteBanner(tenant, banner)) throw new ContentException();
    home.reorder(tenant, home.banners(tenant).stream().map(BannerSummary::id).toList());
    return home.banners(tenant);
  }

  /** {@code order} must list every banner of the store exactly once. */
  @Transactional
  public List<BannerSummary> reorder(UUID actor, UUID tenant, List<UUID> order) {
    changes.changed(tenant);
    store(actor, tenant);
    home.lockBanners(tenant);
    var current = home.banners(tenant).stream().map(BannerSummary::id).toList();
    if (order.size() != current.size() || !new HashSet<>(order).equals(new HashSet<>(current)))
      throw new IllegalArgumentException("The order must list every banner once");
    home.reorder(tenant, order);
    return home.banners(tenant);
  }

  @Transactional
  public List<BannerSummary> uploadImage(
      UUID actor,
      UUID tenant,
      UUID banner,
      boolean mobile,
      Supplier<byte[]> bytes,
      String filename,
      String declaredType) {
    changes.changed(tenant);
    store(actor, tenant);
    ImageContent image = normalizer.normalize(bytes.get(), filename, declaredType);
    if (!home.saveImage(tenant, banner, mobile, image)) throw new ContentException();
    return home.banners(tenant);
  }

  @Transactional
  public ImageContent image(UUID actor, UUID tenant, UUID banner, boolean mobile) {
    store(actor, tenant);
    return home.image(tenant, banner, mobile).orElseThrow(ContentException::new);
  }

  @Transactional
  public List<HomeSection> sections(UUID actor, UUID tenant) {
    store(actor, tenant);
    return home.sections(tenant);
  }

  @Transactional
  public List<HomeSection> replaceSections(UUID actor, UUID tenant, List<HomeSection> sections) {
    changes.changed(tenant);
    UUID store = store(actor, tenant);
    try {
      home.replaceSections(store, tenant, HomeSection.list(sections));
    } catch (DataIntegrityViolationException foreign) {
      throw new ContentException(); // a category of another tenant is the same as none
    }
    return home.sections(tenant);
  }

  private UUID store(UUID actor, UUID tenant) {
    return access.lockOwnedStore(actor, tenant).orElseThrow(ContentException::new);
  }
}
