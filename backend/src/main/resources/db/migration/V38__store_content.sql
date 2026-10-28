-- Sprint 8, SC-043 (ADR-0018 V8, V13): what the store shows about the company in its storefront
-- footer, the announcement bar, and its institutional pages. Additive; one optional row per store.
-- The application normalizes every value; the CHECKs only keep the stored shape.
CREATE TABLE store_company (
    store_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    announcement VARCHAR(120) CHECK (announcement IS NULL OR char_length(btrim(announcement)) BETWEEN 1 AND 120),
    legal_name VARCHAR(120) CHECK (legal_name IS NULL OR char_length(btrim(legal_name)) BETWEEN 1 AND 120),
    cnpj CHAR(14) CHECK (cnpj IS NULL OR cnpj ~ '^[0-9]{14}$'),
    phone VARCHAR(13) CHECK (phone IS NULL OR phone ~ '^55[1-9][0-9]{9,10}$'),
    address VARCHAR(240) CHECK (address IS NULL OR char_length(btrim(address)) BETWEEN 1 AND 240),
    facebook VARCHAR(100) CHECK (facebook IS NULL OR facebook ~ '^[A-Za-z0-9.]{1,100}$'),
    tiktok VARCHAR(30) CHECK (tiktok IS NULL OR tiktok ~ '^[A-Za-z0-9._]{1,30}$'),
    youtube VARCHAR(100) CHECK (youtube IS NULL OR youtube ~ '^[A-Za-z0-9._-]{1,100}$'),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT store_company_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE CASCADE
);

-- Institutional pages of fixed kinds; body is the sanitized rich text of product descriptions.
CREATE TABLE store_page (
    store_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ABOUT','RETURNS','PRIVACY','TERMS','SHIPPING')),
    body_html TEXT NOT NULL CHECK (char_length(body_html) BETWEEN 1 AND 20000),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (store_id, kind),
    CONSTRAINT store_page_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE CASCADE
);
