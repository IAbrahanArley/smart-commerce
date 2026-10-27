-- MVP delivery, block 1c (RELEASE_PLAN.md; PRD item 3): flat product categories per store.
-- Additive. A product has at most one category; removing a category first clears it from its
-- products (application, same transaction), so the foreign key stays RESTRICT. The composite key
-- keeps a product from ever pointing at another tenant's category.
CREATE TABLE catalog_category (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    name VARCHAR(60) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 60),
    -- Case-insensitive uniqueness per store ("Camisetas" and "camisetas" are the same category).
    name_key VARCHAR(60) NOT NULL CHECK (name_key = lower(btrim(name))),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT catalog_category_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT catalog_category_id_tenant_unique UNIQUE (id, tenant_id),
    CONSTRAINT catalog_category_name_unique UNIQUE (tenant_id, name_key)
);

ALTER TABLE catalog_product ADD COLUMN category_id UUID;
ALTER TABLE catalog_product ADD CONSTRAINT catalog_product_category_fk
    FOREIGN KEY (category_id, tenant_id) REFERENCES catalog_category(id, tenant_id) ON DELETE RESTRICT;
CREATE INDEX catalog_product_category_idx ON catalog_product(tenant_id, category_id)
    WHERE category_id IS NOT NULL;
