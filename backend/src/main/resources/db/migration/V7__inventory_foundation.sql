-- Product stock and immutable application ledger; no orders, reservations or storefront.
ALTER TABLE catalog_product ADD CONSTRAINT catalog_product_id_tenant_unique UNIQUE (id, tenant_id);
CREATE TABLE inventory_balance (
 product_id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
 CONSTRAINT inventory_balance_product_tenant_fk FOREIGN KEY (product_id,tenant_id)
 REFERENCES catalog_product(id,tenant_id) ON DELETE RESTRICT
);
CREATE TABLE inventory_movement (
 sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id UUID NOT NULL UNIQUE,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL,
 actor_id UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 request_id UUID NOT NULL,
 type VARCHAR(16) NOT NULL CHECK (type IN ('ENTRY','ADJUSTMENT')),
 delta INTEGER NOT NULL CHECK (delta <> 0 AND (type <> 'ENTRY' OR delta > 0)),
 balance_after INTEGER NOT NULL CHECK (balance_after >= 0),
 reason VARCHAR(240) NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 1 AND 240),
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT inventory_movement_product_tenant_fk FOREIGN KEY (product_id,tenant_id)
 REFERENCES catalog_product(id,tenant_id) ON DELETE RESTRICT,
 CONSTRAINT inventory_movement_tenant_request_unique UNIQUE (tenant_id,request_id)
);
CREATE INDEX inventory_movement_history_idx ON inventory_movement(tenant_id,product_id,sequence DESC);
