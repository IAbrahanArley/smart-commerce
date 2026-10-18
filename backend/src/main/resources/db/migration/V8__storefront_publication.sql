-- Publication is explicit and reversible; existing and newly created products remain hidden.
ALTER TABLE store DROP CONSTRAINT store_status_check;
ALTER TABLE store ADD CONSTRAINT store_status_check CHECK (status IN ('DRAFT','PUBLISHED'));
ALTER TABLE catalog_product ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX catalog_product_public_listing_idx ON catalog_product(store_id,id) WHERE published;
