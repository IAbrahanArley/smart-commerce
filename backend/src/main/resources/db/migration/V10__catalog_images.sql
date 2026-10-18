-- Normalized product photos; bytes and metadata share the database transaction and backups.
CREATE TABLE catalog_product_image (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL,
 product_id UUID NOT NULL,
 alt VARCHAR(200) NOT NULL CHECK (char_length(btrim(alt)) BETWEEN 1 AND 200),
 width INTEGER NOT NULL CHECK (width BETWEEN 1 AND 2048),
 height INTEGER NOT NULL CHECK (height BETWEEN 1 AND 2048),
 media_type VARCHAR(16) NOT NULL CHECK (media_type IN ('image/jpeg','image/png')),
 content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
 position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 5),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT catalog_image_product_tenant_fk FOREIGN KEY(product_id,tenant_id)
 REFERENCES catalog_product(id,tenant_id) ON DELETE RESTRICT
);
CREATE INDEX catalog_image_listing_idx ON catalog_product_image(tenant_id,product_id,position,id);
