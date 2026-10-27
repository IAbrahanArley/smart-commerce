-- Sprint 8, SC-041 (ADR-0018 V2, V3, V5): brands, the storefront address of a product and the
-- "compare at" price. Additive. Existing products get a slug derived from their name.

-- V2: brands of a store. Removing a brand clears it from its products first (application).
CREATE TABLE catalog_brand (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    name VARCHAR(60) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 60),
    name_key VARCHAR(60) NOT NULL CHECK (name_key = lower(btrim(name))),
    slug VARCHAR(80) COLLATE "C" NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT catalog_brand_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT catalog_brand_id_tenant_unique UNIQUE (id, tenant_id),
    CONSTRAINT catalog_brand_name_unique UNIQUE (tenant_id, name_key),
    CONSTRAINT catalog_brand_slug_unique UNIQUE (store_id, slug)
);

ALTER TABLE catalog_product ADD COLUMN brand_id UUID;
ALTER TABLE catalog_product ADD CONSTRAINT catalog_product_brand_fk
    FOREIGN KEY (brand_id, tenant_id) REFERENCES catalog_brand(id, tenant_id) ON DELETE RESTRICT;
CREATE INDEX catalog_product_brand_idx ON catalog_product(tenant_id, brand_id)
    WHERE brand_id IS NOT NULL;

-- V5: the product's address in the storefront, unique in the store. Nullable so that rows
-- written outside the application keep working; the application always sets it.
ALTER TABLE catalog_product ADD COLUMN slug VARCHAR(80) COLLATE "C"
    CHECK (slug IS NULL OR slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

-- Cut to 60 in the same statement: names reach 120 characters, longer than the column.
UPDATE catalog_product SET slug = coalesce(nullif(rtrim(left(btrim(regexp_replace(
    translate(lower(name),
        'áàâãäåçéèêëíìîïñóòôõöúùûüýÿ',
        'aaaaaaceeeeiiiinooooouuuuyy'),
    '[^a-z0-9]+', '-', 'g'), '-'), 60), '-'), ''), 'produto');

-- Products whose names fold to the same slug: the later ones get the start of their id.
WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY store_id, slug ORDER BY created_at, id) AS n
    FROM catalog_product)
UPDATE catalog_product p SET slug = p.slug || '-' || left(p.id::text, 8)
FROM numbered WHERE numbered.id = p.id AND numbered.n > 1;

CREATE UNIQUE INDEX catalog_product_slug_unique ON catalog_product(store_id, slug)
    WHERE slug IS NOT NULL;

-- Former addresses, so old links can be redirected (SC-045) for 90 days after a change.
CREATE TABLE catalog_product_slug_history (
    store_id UUID NOT NULL,
    slug VARCHAR(80) COLLATE "C" NOT NULL,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    replaced_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (store_id, slug),
    CONSTRAINT catalog_product_slug_history_product_fk FOREIGN KEY (product_id)
        REFERENCES catalog_product(id) ON DELETE CASCADE
);

-- V3: "compare at" price per variant (a simple product has exactly one variant). Display only:
-- the storefront shows it when it is greater than the current price; the charge is the price.
ALTER TABLE catalog_variant ADD COLUMN compare_at_price NUMERIC
    CHECK (compare_at_price IS NULL OR (compare_at_price > 0
        AND compare_at_price < 100000000000000000 AND scale(compare_at_price) <= 2));
