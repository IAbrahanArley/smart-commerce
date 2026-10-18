-- SC-006 (ADR-0012 D3): pickup and one fixed shipping fee per store, without postal-code rules.
-- Additive 1:1 table; a store without a row has every option disabled and version 0.
-- The composite reference keeps the configuration inside the store's own tenant; store.tenant_id
-- is already unique, so the primary key is the only conflict target of the first save.
CREATE TABLE store_fulfillment (
    store_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    pickup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    pickup_instructions VARCHAR(500)
        CHECK (pickup_instructions IS NULL OR char_length(btrim(pickup_instructions)) BETWEEN 1 AND 500),
    fixed_shipping_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- No numeric typmod: PostgreSQL must reject extra decimal places instead of rounding them.
    -- Zero means free shipping.
    fixed_shipping_amount NUMERIC CHECK (fixed_shipping_amount IS NULL OR (
        fixed_shipping_amount >= 0 AND fixed_shipping_amount < 100000000000000000
        AND scale(fixed_shipping_amount) <= 2)),
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL' CHECK (currency = 'BRL'),
    fixed_shipping_area VARCHAR(500)
        CHECK (fixed_shipping_area IS NULL OR char_length(btrim(fixed_shipping_area)) BETWEEN 1 AND 500),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT store_fulfillment_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
        REFERENCES store(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT store_fulfillment_pickup_check
        CHECK (NOT pickup_enabled OR pickup_instructions IS NOT NULL),
    CONSTRAINT store_fulfillment_shipping_check
        CHECK (NOT fixed_shipping_enabled OR (fixed_shipping_amount IS NOT NULL AND fixed_shipping_area IS NOT NULL))
);
