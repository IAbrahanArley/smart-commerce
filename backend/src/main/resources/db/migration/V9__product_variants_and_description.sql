-- Additive evolution: existing sellable IDs and every stock/ledger row keep their identity.
ALTER TABLE catalog_product ADD COLUMN description_html TEXT NOT NULL DEFAULT ''
 CHECK (char_length(description_html)<=20000 AND octet_length(description_html)<=80000);
ALTER TABLE catalog_product ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'SIMPLE'
 CHECK (kind IN ('SIMPLE','VARIABLE'));
CREATE TABLE catalog_variant (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL,
 sku VARCHAR(64) COLLATE "C" NOT NULL CHECK(sku ~ '^[A-Za-z0-9._/-]{1,64}$'),
 price NUMERIC NOT NULL CHECK(price>0 AND price<100000000000000000 AND scale(price)<=2),
 cost NUMERIC CHECK(cost IS NULL OR(cost>=0 AND cost<100000000000000000 AND scale(cost)<=2)),
 currency VARCHAR(3) NOT NULL DEFAULT 'BRL' CHECK(currency='BRL'),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 options_json TEXT NOT NULL CHECK(jsonb_typeof(options_json::jsonb)='array'),
 combination_key TEXT NOT NULL,
 CONSTRAINT catalog_variant_product_tenant_fk FOREIGN KEY(product_id,tenant_id)
 REFERENCES catalog_product(id,tenant_id) ON DELETE RESTRICT,
 CONSTRAINT catalog_variant_id_tenant_unique UNIQUE(id,tenant_id),
 CONSTRAINT catalog_variant_tenant_sku_unique UNIQUE(tenant_id,sku),
 CONSTRAINT catalog_variant_combination_unique UNIQUE(product_id,combination_key)
);
INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key)
 SELECT id,tenant_id,id,sku,price,cost,currency,'[]','' FROM catalog_product;
ALTER TABLE catalog_product DROP CONSTRAINT catalog_product_tenant_sku_unique;
ALTER TABLE inventory_balance DROP CONSTRAINT inventory_balance_product_tenant_fk;
ALTER TABLE inventory_balance ADD CONSTRAINT inventory_balance_variant_tenant_fk
 FOREIGN KEY(product_id,tenant_id) REFERENCES catalog_variant(id,tenant_id) ON DELETE RESTRICT;
ALTER TABLE inventory_movement DROP CONSTRAINT inventory_movement_product_tenant_fk;
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_variant_tenant_fk
 FOREIGN KEY(product_id,tenant_id) REFERENCES catalog_variant(id,tenant_id) ON DELETE RESTRICT;
CREATE INDEX catalog_variant_product_idx ON catalog_variant(tenant_id,product_id,id);
