-- SC-013 (ADR-0013 P1, P2, P8, P11): one Checkout Pro order at Mercado Pago per sales order.
-- Additive: one new table, no existing data changes. No payer data (CPF, card, e-mail) is stored;
-- the provider collects it.

-- The row is claimed (CREATING) before calling the provider, so concurrent attempts for one order
-- share it; the provider call is idempotent by a key derived from the order. CREATED holds the
-- provider order and the checkout URL the buyer is redirected to. Confirmation states arrive with
-- SC-014.
CREATE TABLE payment (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL,
 order_id UUID NOT NULL,
 provider VARCHAR(20) NOT NULL CHECK (provider = 'MERCADO_PAGO'),
 status VARCHAR(20) NOT NULL CHECK (status IN ('CREATING','CREATED')),
 currency VARCHAR(3) NOT NULL CHECK (currency = 'BRL'),
 amount NUMERIC NOT NULL
  CHECK (amount > 0 AND amount < 100000000000000000 AND scale(amount) <= 2),
 -- Seller account the provider order was created with; SC-014 checks notifications against it.
 provider_user_id VARCHAR(40) CHECK (provider_user_id ~ '^[0-9]{1,40}$'),
 provider_order_id VARCHAR(64) CHECK (provider_order_id ~ '^[A-Za-z0-9_-]{1,64}$'),
 provider_status VARCHAR(40),
 checkout_url VARCHAR(2048) CHECK (checkout_url LIKE 'https://%'),
 provider_expires_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
 CONSTRAINT payment_order_tenant_fk FOREIGN KEY (order_id, tenant_id)
 REFERENCES sales_order(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT payment_order_unique UNIQUE (order_id),
 CONSTRAINT payment_provider_order_unique UNIQUE (provider, provider_order_id),
 CONSTRAINT payment_created_check CHECK (
  (status = 'CREATED') = (provider_order_id IS NOT NULL)
  AND (provider_order_id IS NULL) = (provider_user_id IS NULL)
  AND (provider_order_id IS NULL) = (checkout_url IS NULL)
  AND (provider_order_id IS NULL) = (provider_expires_at IS NULL))
);
CREATE INDEX payment_tenant_idx ON payment(tenant_id);
