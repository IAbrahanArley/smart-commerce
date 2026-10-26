-- MVP delivery, block 1b (RELEASE_PLAN.md; PRD item 10): store contacts and logo, shown on the
-- storefront. Additive; one optional row per store, created on the first save (version 1).
-- The logo follows the product photos (ADR-0011): normalized JPEG/PNG bytes in the database,
-- sharing transactions and backups. Contacts are public by design: the store chooses to show them.
CREATE TABLE store_profile (
    store_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    contact_email VARCHAR(254)
        CHECK (contact_email IS NULL OR contact_email ~ '^[^@[:space:]]{1,64}@[^@[:space:]]{1,189}$'),
    -- Brazilian mobile or landline in E.164 digits: 55 + area code + number.
    whatsapp VARCHAR(13) CHECK (whatsapp IS NULL OR whatsapp ~ '^55[1-9][0-9]{9,10}$'),
    instagram VARCHAR(30) CHECK (instagram IS NULL OR instagram ~ '^[A-Za-z0-9._]{1,30}$'),
    logo_media_type VARCHAR(16) CHECK (logo_media_type IS NULL OR logo_media_type IN ('image/jpeg','image/png')),
    logo_content BYTEA CHECK (logo_content IS NULL OR octet_length(logo_content) BETWEEN 1 AND 5242880),
    logo_width INTEGER CHECK (logo_width IS NULL OR logo_width BETWEEN 1 AND 2048),
    logo_height INTEGER CHECK (logo_height IS NULL OR logo_height BETWEEN 1 AND 2048),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT store_profile_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT store_profile_logo_check CHECK (
        (logo_content IS NULL) = (logo_media_type IS NULL)
        AND (logo_content IS NULL) = (logo_width IS NULL)
        AND (logo_content IS NULL) = (logo_height IS NULL))
);
CREATE INDEX store_profile_tenant_idx ON store_profile(tenant_id);
