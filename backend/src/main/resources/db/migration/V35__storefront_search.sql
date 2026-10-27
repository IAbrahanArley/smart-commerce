-- Sprint 8, SC-042 (ADR-0018 V6, V7): storefront search and "best sellers". Additive.

-- V6: accent-insensitive search ("oculos" finds "Óculos"). unaccent is a trusted contrib
-- extension (PostgreSQL 13+): the database owner can create it without superuser rights.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() is only STABLE (its dictionary could change), so an index cannot use it directly.
-- This wrapper pins the dictionary and is declared IMMUTABLE, the documented way to index it.
CREATE FUNCTION sc_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

CREATE INDEX catalog_product_search_idx ON catalog_product
    USING gin (to_tsvector('portuguese', sc_unaccent(name)));

-- V7: units sold per product in paid orders, summed for the last 90 days.
CREATE INDEX sales_order_item_product_idx ON sales_order_item(product_id, tenant_id);
