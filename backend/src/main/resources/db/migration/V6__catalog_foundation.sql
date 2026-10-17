-- Basic catalog authorized separately from Sprint 1: no variants, stock or public storefront.
-- Composite references prevent associating a product with a store in another tenant.
ALTER TABLE store ADD CONSTRAINT store_id_tenant_unique UNIQUE (id, tenant_id);

CREATE TABLE catalog_product (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    store_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 120),
    sku VARCHAR(64) COLLATE "C" NOT NULL CHECK (sku ~ '^[A-Za-z0-9._/-]{1,64}$'),
    -- No numeric typmod: PostgreSQL must reject extra decimal places instead of rounding them.
    price NUMERIC NOT NULL CHECK (price > 0 AND price < 100000000000000000 AND scale(price) <= 2),
    cost NUMERIC CHECK (cost IS NULL OR (cost >= 0 AND cost < 100000000000000000 AND scale(cost) <= 2)),
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL' CHECK (currency = 'BRL'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL CHECK (updated_at >= created_at),
    CONSTRAINT catalog_product_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT catalog_product_tenant_sku_unique UNIQUE (tenant_id, sku)
);
