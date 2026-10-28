-- Sprint 8, SC-043 (ADR-0018 V8): the store home. Additive.
--  * banners: up to 5 per store (application), each with a desktop image and an optional phone
--    image (normalized JPEG/PNG like product photos), required alternative text, an internal link
--    only and an optional period;
--  * sections: the ordered blocks of the home, of fixed kinds;
--  * featured: products the owner marks for the "Destaques" section.
CREATE TABLE store_banner (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    alt VARCHAR(200) NOT NULL CHECK (char_length(btrim(alt)) BETWEEN 1 AND 200),
    title VARCHAR(80) CHECK (title IS NULL OR char_length(btrim(title)) BETWEEN 1 AND 80),
    link_kind VARCHAR(10) NOT NULL CHECK (link_kind IN ('NONE','CATEGORY','PRODUCT','SEARCH')),
    link_target VARCHAR(100),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    desktop_media_type VARCHAR(16) CHECK (desktop_media_type IS NULL OR desktop_media_type IN ('image/jpeg','image/png')),
    desktop_content BYTEA CHECK (desktop_content IS NULL OR octet_length(desktop_content) BETWEEN 1 AND 5242880),
    desktop_width INTEGER,
    desktop_height INTEGER,
    mobile_media_type VARCHAR(16) CHECK (mobile_media_type IS NULL OR mobile_media_type IN ('image/jpeg','image/png')),
    mobile_content BYTEA CHECK (mobile_content IS NULL OR octet_length(mobile_content) BETWEEN 1 AND 5242880),
    mobile_width INTEGER,
    mobile_height INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((link_kind = 'NONE') = (link_target IS NULL)),
    CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at),
    CONSTRAINT store_banner_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX store_banner_tenant_idx ON store_banner(tenant_id, position);

CREATE TABLE store_home_section (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('NEW','BEST','FEATURED','CATEGORY')),
    title VARCHAR(60) NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 60),
    category_id UUID,
    CHECK ((kind = 'CATEGORY') = (category_id IS NOT NULL)),
    CONSTRAINT store_home_section_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE CASCADE,
    -- Removing the category removes its section.
    CONSTRAINT store_home_section_category_fk FOREIGN KEY (category_id, tenant_id)
        REFERENCES catalog_category(id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX store_home_section_tenant_idx ON store_home_section(tenant_id, position);

ALTER TABLE catalog_product ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;
