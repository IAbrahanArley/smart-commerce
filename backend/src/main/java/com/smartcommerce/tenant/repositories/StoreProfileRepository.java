package com.smartcommerce.tenant.repositories;

import com.smartcommerce.catalog.domain.ImageContent;
import com.smartcommerce.tenant.domain.StoreProfile;
import com.smartcommerce.tenant.domain.StoreProfileSettings;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException.Reason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Store profile (MVP block 1b). Reads and writes go through the OWNER's active, verified
 * membership; writes are optimistic like {@link FulfillmentRepository}: the first save inserts
 * version 1 only when no row exists, later ones update only the expected version. The store name
 * lives in {@code store} and changes in the same transaction.
 */
@Repository
public class StoreProfileRepository {
  private final JdbcTemplate jdbc;
  private final TenantAccessRepository access;

  public StoreProfileRepository(JdbcTemplate jdbc, TenantAccessRepository access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  public Optional<StoreProfile> findOwned(UUID actor, UUID tenant) {
    return jdbc
        .query(
            """
            SELECT s.name,p.contact_email,p.whatsapp,p.instagram,p.logo_content IS NOT NULL AS logo,
              COALESCE(p.version,0) AS version
            FROM store s
            JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=? AND m.role='OWNER'
            JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            LEFT JOIN store_profile p ON p.store_id=s.id AND p.tenant_id=s.tenant_id
            WHERE s.tenant_id=?
            """,
            (row, index) ->
                new StoreProfile(
                    new StoreProfileSettings(
                        row.getString("name"),
                        row.getString("contact_email"),
                        row.getString("whatsapp"),
                        row.getString("instagram")),
                    row.getBoolean("logo"),
                    row.getLong("version")),
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  /** Returns false on a stale version; authorization locks last until the transaction ends. */
  public boolean save(
      UUID actor, UUID tenant, StoreProfileSettings settings, long version, Instant now) {
    UUID store = lockedStore(actor, tenant);
    Timestamp updatedAt = Timestamp.from(now);
    boolean saved =
        version == 0
            ? jdbc.update(
                    """
                    INSERT INTO store_profile(store_id,tenant_id,contact_email,whatsapp,instagram,
                      version,updated_at)
                    VALUES(?,?,?,?,?,1,?) ON CONFLICT(store_id) DO NOTHING
                    """,
                    store,
                    tenant,
                    settings.contactEmail(),
                    settings.whatsapp(),
                    settings.instagram(),
                    updatedAt)
                == 1
            : jdbc.update(
                    """
                    UPDATE store_profile SET contact_email=?,whatsapp=?,instagram=?,
                      version=version+1,updated_at=?
                    WHERE store_id=? AND tenant_id=? AND version=?
                    """,
                    settings.contactEmail(),
                    settings.whatsapp(),
                    settings.instagram(),
                    updatedAt,
                    store,
                    tenant,
                    version)
                == 1;
    if (saved)
      jdbc.update(
          "UPDATE store SET name=? WHERE id=? AND tenant_id=?", settings.name(), store, tenant);
    return saved;
  }

  /** Sets ({@code logo} present) or removes the logo; false on a stale version. */
  public boolean saveLogo(UUID actor, UUID tenant, ImageContent logo, long version, Instant now) {
    UUID store = lockedStore(actor, tenant);
    Timestamp updatedAt = Timestamp.from(now);
    if (version == 0) {
      if (logo == null) return false;
      return jdbc.update(
              """
              INSERT INTO store_profile(store_id,tenant_id,logo_media_type,logo_content,logo_width,
                logo_height,version,updated_at)
              VALUES(?,?,?,?,?,?,1,?) ON CONFLICT(store_id) DO NOTHING
              """,
              store,
              tenant,
              logo.mediaType(),
              logo.bytes(),
              logo.width(),
              logo.height(),
              updatedAt)
          == 1;
    }
    return jdbc.update(
            """
            UPDATE store_profile SET logo_media_type=?,logo_content=?,logo_width=?,logo_height=?,
              version=version+1,updated_at=?
            WHERE store_id=? AND tenant_id=? AND version=?
            """,
            logo == null ? null : logo.mediaType(),
            logo == null ? null : logo.bytes(),
            logo == null ? null : logo.width(),
            logo == null ? null : logo.height(),
            updatedAt,
            store,
            tenant,
            version)
        == 1;
  }

  /** The owner's logo for the admin preview. */
  public Optional<ImageContent> ownedLogo(UUID actor, UUID tenant) {
    return jdbc
        .query(
            """
            SELECT p.logo_content,p.logo_media_type,p.logo_width,p.logo_height
            FROM store_profile p
            JOIN tenant_membership m ON m.tenant_id=p.tenant_id AND m.user_id=? AND m.role='OWNER'
            JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            WHERE p.tenant_id=? AND p.logo_content IS NOT NULL
            """,
            (row, index) ->
                new ImageContent(row.getBytes(1), row.getString(2), row.getInt(3), row.getInt(4)),
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  private UUID lockedStore(UUID actor, UUID tenant) {
    return access
        .lockOwnedStore(actor, tenant)
        .orElseThrow(() -> new TenantPersistenceException(Reason.MISSING));
  }
}
