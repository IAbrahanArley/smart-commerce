-- SC-008 (ADR-0012 §3, §5, §6): public checkout creates a PENDING_PAYMENT order with immutable
-- snapshots. Money follows the catalog contract: NUMERIC without typmod so PostgreSQL rejects
-- extra decimal places instead of rounding them.

-- Human-readable numbering per store, allocated under the counter row lock.
CREATE TABLE sales_order_counter (
 store_id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL,
 last_number BIGINT NOT NULL CHECK (last_number >= 0),
 CONSTRAINT sales_order_counter_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
 REFERENCES store(id, tenant_id) ON DELETE RESTRICT
);

CREATE TABLE sales_order (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL,
 store_id UUID NOT NULL,
 number BIGINT NOT NULL CHECK (number > 0),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_PAYMENT','EXPIRED','CANCELLED')),
 currency VARCHAR(3) NOT NULL CHECK (currency = 'BRL'),
 subtotal_amount NUMERIC NOT NULL
  CHECK (subtotal_amount > 0 AND subtotal_amount < 100000000000000000 AND scale(subtotal_amount) <= 2),
 shipping_amount NUMERIC NOT NULL
  CHECK (shipping_amount >= 0 AND shipping_amount < 100000000000000000 AND scale(shipping_amount) <= 2),
 discount_amount NUMERIC NOT NULL CHECK (discount_amount = 0),
 total_amount NUMERIC NOT NULL
  CHECK (total_amount > 0 AND scale(total_amount) <= 2),
 fulfillment_type VARCHAR(20) NOT NULL CHECK (fulfillment_type IN ('PICKUP','FIXED_SHIPPING')),
 -- Snapshot of the store text shown to the buyer: pickup instructions or the informative area.
 fulfillment_text VARCHAR(500) NOT NULL CHECK (char_length(btrim(fulfillment_text)) BETWEEN 1 AND 500),
 -- Buyer data (D2): minimal, snapshot only, never shared across stores; no Customer table.
 buyer_name VARCHAR(480) NOT NULL CHECK (char_length(btrim(buyer_name)) BETWEEN 1 AND 120),
 buyer_email VARCHAR(254) NOT NULL CHECK (char_length(buyer_email) BETWEEN 3 AND 254),
 buyer_email_key VARCHAR(254) COLLATE "C" NOT NULL,
 buyer_phone VARCHAR(16) NOT NULL CHECK (buyer_phone ~ '^\+?[0-9]{10,15}$'),
 address_line1 VARCHAR(800),
 address_line2 VARCHAR(800),
 address_district VARCHAR(400),
 address_city VARCHAR(400),
 address_state VARCHAR(2) CHECK (address_state IS NULL OR address_state ~ '^[A-Z]{2}$'),
 address_postal_code VARCHAR(8) CHECK (address_postal_code IS NULL OR address_postal_code ~ '^[0-9]{8}$'),
 reservation_expires_at TIMESTAMPTZ NOT NULL,
 request_id UUID NOT NULL,
 request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
 access_token_hash CHAR(64) NOT NULL CHECK (access_token_hash ~ '^[0-9a-f]{64}$'),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 CONSTRAINT sales_order_store_tenant_fk FOREIGN KEY (store_id, tenant_id)
 REFERENCES store(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT sales_order_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT sales_order_store_number_unique UNIQUE (store_id, number),
 CONSTRAINT sales_order_tenant_request_unique UNIQUE (tenant_id, request_id),
 CONSTRAINT sales_order_total_check CHECK (total_amount = subtotal_amount + shipping_amount - discount_amount),
 CONSTRAINT sales_order_window_check CHECK (reservation_expires_at > created_at),
 -- Address exists exactly for fixed shipping; pickup orders never store an address.
 CONSTRAINT sales_order_address_check CHECK (
  (fulfillment_type = 'FIXED_SHIPPING') = (address_line1 IS NOT NULL)
  AND (address_line1 IS NULL) = (address_district IS NULL)
  AND (address_line1 IS NULL) = (address_city IS NULL)
  AND (address_line1 IS NULL) = (address_state IS NULL)
  AND (address_line1 IS NULL) = (address_postal_code IS NULL)
  AND (address_line1 IS NOT NULL OR address_line2 IS NULL))
);
-- Per-store pending count by buyer e-mail (D4) and buyer-facing reads by id.
CREATE INDEX sales_order_pending_email_idx ON sales_order(store_id, buyer_email_key, reservation_expires_at)
 WHERE status = 'PENDING_PAYMENT';

CREATE TABLE sales_order_item (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL,
 order_id UUID NOT NULL,
 variant_id UUID NOT NULL,
 product_id UUID NOT NULL,
 position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 19),
 product_name_snapshot VARCHAR(480) NOT NULL,
 variant_options_snapshot TEXT NOT NULL,
 sku_snapshot VARCHAR(64) COLLATE "C" NOT NULL,
 unit_price_snapshot NUMERIC NOT NULL
  CHECK (unit_price_snapshot > 0 AND unit_price_snapshot < 100000000000000000 AND scale(unit_price_snapshot) <= 2),
 -- NULL cost means margin unavailable, never presumed profit (BUSINESS_RULES).
 unit_cost_snapshot NUMERIC
  CHECK (unit_cost_snapshot IS NULL OR (unit_cost_snapshot >= 0 AND unit_cost_snapshot < 100000000000000000 AND scale(unit_cost_snapshot) <= 2)),
 quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
 line_total NUMERIC NOT NULL CHECK (scale(line_total) <= 2),
 CONSTRAINT sales_order_item_order_tenant_fk FOREIGN KEY (order_id, tenant_id)
 REFERENCES sales_order(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT sales_order_item_variant_tenant_fk FOREIGN KEY (variant_id, tenant_id)
 REFERENCES catalog_variant(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT sales_order_item_order_variant_unique UNIQUE (order_id, variant_id),
 CONSTRAINT sales_order_item_order_position_unique UNIQUE (order_id, position),
 CONSTRAINT sales_order_item_total_check CHECK (line_total = unit_price_snapshot * quantity)
);

-- Pending since SC-005: every reservation belongs to an order of the same tenant. NOT VALID keeps
-- this migration additive on databases holding reservations created before orders existed (for
-- example local fixtures); PostgreSQL still enforces it for every new or updated row.
ALTER TABLE inventory_reservation ADD CONSTRAINT inventory_reservation_order_tenant_fk
 FOREIGN KEY (order_id, tenant_id) REFERENCES sales_order(id, tenant_id) ON DELETE RESTRICT NOT VALID;

-- Checkout admission counters (D4). Keys are HMACs with an ephemeral in-memory key, never raw
-- IP addresses or e-mails; rows expire after their window.
CREATE TABLE order_rate_limit (
 bucket_key CHAR(64) PRIMARY KEY CHECK (bucket_key ~ '^[0-9a-f]{64}$'),
 window_start TIMESTAMPTZ NOT NULL,
 attempts INTEGER NOT NULL CHECK (attempts >= 0),
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX order_rate_limit_expiry_idx ON order_rate_limit(expires_at);
