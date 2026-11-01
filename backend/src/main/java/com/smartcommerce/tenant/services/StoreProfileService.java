package com.smartcommerce.tenant.services;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.catalog.domain.ImageNormalizer;
import com.smartcommerce.storefront.services.StorefrontChanges;
import com.smartcommerce.tenant.domain.StoreProfile;
import com.smartcommerce.tenant.domain.StoreProfileSettings;
import com.smartcommerce.tenant.dto.response.StoreProfileView;
import com.smartcommerce.tenant.exceptions.StoreProfileException;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException;
import com.smartcommerce.tenant.repositories.StoreProfileRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Store name, public contacts and logo (MVP block 1b, PRD item 10). Only the store OWNER reads or
 * changes them; the tenant comes from the route and is trusted only through that membership. The
 * logo goes through the same {@link ImageNormalizer} as product photos (re-encoded JPEG/PNG,
 * metadata dropped) before it is stored.
 */
@Service
public class StoreProfileService {
  /** Logos are often small or wide (for example 240 × 80); photos keep their own minimum. */
  static final int LOGO_MIN_SIDE = 64;

  private final StoreProfileRepository profiles;
  private final ImageNormalizer normalizer;
  private final TransactionTemplate writes;

  private final StorefrontChanges changes;

  public StoreProfileService(
      StoreProfileRepository profiles,
      ImageNormalizer normalizer,
      PlatformTransactionManager transactions,
      StorefrontChanges changes) {
    this.changes = changes;
    this.profiles = profiles;
    this.normalizer = normalizer;
    this.writes = new TransactionTemplate(transactions);
  }

  @Transactional(readOnly = true)
  public StoreProfileView find(UUID actor, UUID tenant) {
    return view(profiles.findOwned(actor, tenant).orElseThrow(StoreProfileService::missing));
  }

  @Transactional
  public StoreProfileView update(
      UUID actor, UUID tenant, StoreProfileSettings settings, long version) {
    changes.changed(tenant);
    checkVersion(version);
    boolean saved;
    try {
      saved = profiles.save(actor, tenant, settings, version, Instant.now());
    } catch (TenantPersistenceException exception) {
      throw missing();
    }
    if (!saved) throw conflict();
    return find(actor, tenant);
  }

  /** Normalizes outside the write so a slow image never holds the store lock. */
  public StoreProfileView uploadLogo(
      UUID actor,
      UUID tenant,
      long version,
      Supplier<byte[]> bytes,
      String filename,
      String declaredType) {
    checkVersion(version);
    ImageContent logo = normalizer.normalize(bytes.get(), filename, declaredType, LOGO_MIN_SIDE);
    return writeLogo(actor, tenant, logo, version);
  }

  public StoreProfileView removeLogo(UUID actor, UUID tenant, long version) {
    checkVersion(version);
    return writeLogo(actor, tenant, null, version);
  }

  @Transactional(readOnly = true)
  public ImageContent logo(UUID actor, UUID tenant) {
    return profiles
        .ownedLogo(actor, tenant)
        .orElseThrow(
            () ->
                new StoreProfileException(
                    StoreProfileException.Reason.NO_LOGO, "A loja não tem logo."));
  }

  private StoreProfileView writeLogo(UUID actor, UUID tenant, ImageContent logo, long version) {
    return writes.execute(
        status -> {
          boolean saved;
          try {
            saved = profiles.saveLogo(actor, tenant, logo, version, Instant.now());
          } catch (TenantPersistenceException exception) {
            throw missing();
          }
          if (!saved) throw conflict();
          return view(profiles.findOwned(actor, tenant).orElseThrow(StoreProfileService::missing));
        });
  }

  private static void checkVersion(long version) {
    if (version < 0 || version == Long.MAX_VALUE)
      throw new IllegalArgumentException("Invalid version");
  }

  private static StoreProfileException missing() {
    return new StoreProfileException(StoreProfileException.Reason.MISSING, "Loja não encontrada.");
  }

  private static StoreProfileException conflict() {
    return new StoreProfileException(
        StoreProfileException.Reason.VERSION_CONFLICT,
        "Os dados da loja foram alterados em outra sessão. Recarregue para ver a versão atual.");
  }

  private static StoreProfileView view(StoreProfile stored) {
    var settings = stored.settings();
    return new StoreProfileView(
        settings.name(),
        settings.contactEmail(),
        settings.whatsapp(),
        settings.instagram(),
        stored.hasLogo(),
        stored.logoWidth(),
        stored.logoHeight(),
        stored.version());
  }
}
