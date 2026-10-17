-- SC-005 (ADR-0012 §1): reservations are intent, not physical facts. inventory_balance and the
-- immutable ledger are untouched; availability = on_hand - ACTIVE reservations not yet expired.
-- order_id has no FK yet: sales_order belongs to SC-008, which must add
-- FOREIGN KEY (order_id, tenant_id) REFERENCES sales_order(id, tenant_id).
CREATE TABLE inventory_reservation (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 variant_id UUID NOT NULL,
 order_id UUID NOT NULL,
 quantity INTEGER NOT NULL CHECK (quantity > 0),
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')),
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 closed_at TIMESTAMPTZ,
 CONSTRAINT inventory_reservation_window_check CHECK (expires_at > created_at),
 CONSTRAINT inventory_reservation_closed_check CHECK ((status = 'ACTIVE') = (closed_at IS NULL)),
 CONSTRAINT inventory_reservation_variant_tenant_fk FOREIGN KEY (variant_id,tenant_id)
 REFERENCES catalog_variant(id,tenant_id) ON DELETE RESTRICT,
 CONSTRAINT inventory_reservation_order_variant_unique UNIQUE (order_id,variant_id)
);
CREATE INDEX inventory_reservation_active_idx ON inventory_reservation(tenant_id,variant_id,expires_at)
 INCLUDE (quantity) WHERE status = 'ACTIVE';
CREATE INDEX inventory_reservation_due_idx ON inventory_reservation(expires_at) WHERE status = 'ACTIVE';

-- Single availability formula shared by inventory and storefront reads. The caller passes the
-- backend Clock instant; reservations stop counting exactly at expires_at, without the routine.
CREATE FUNCTION inventory_reserved_quantity(p_tenant UUID, p_variant UUID, p_at TIMESTAMPTZ)
RETURNS BIGINT LANGUAGE sql STABLE PARALLEL SAFE AS $$
 SELECT COALESCE(SUM(r.quantity),0)::bigint FROM inventory_reservation r
 WHERE r.tenant_id = p_tenant AND r.variant_id = p_variant
  AND r.status = 'ACTIVE' AND r.expires_at > p_at
$$;
CREATE FUNCTION inventory_available_quantity(p_tenant UUID, p_variant UUID, p_at TIMESTAMPTZ)
RETURNS INTEGER LANGUAGE sql STABLE PARALLEL SAFE AS $$
 SELECT GREATEST(0, COALESCE((SELECT b.quantity FROM inventory_balance b
  WHERE b.tenant_id = p_tenant AND b.product_id = p_variant),0)
  - inventory_reserved_quantity(p_tenant, p_variant, p_at))::integer
$$;
