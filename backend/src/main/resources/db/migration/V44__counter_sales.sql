-- SC-053 (ADR-0019 C6, C7, C8): sales registered at the physical store, without a POS, taking
-- the same stock as the storefront. Additive: three new tables, new kinds in the stock, account
-- and ledger tables; no existing row changes.

-- The OWNER's settings of the store's counter sales (decided on 2026-11-03):
--  - staff_discount_limit: the largest discount an employee may give, as a percentage of the
--    sale (0 = none); the OWNER is not limited;
--  - pix_account_id: the account Pix sales go into (no Pix sale without it);
--  - card fee (percent) and release days per card kind, as informed by the store. No fee informed
--    means "taxa indisponível"; no release days, "a receber, data desconhecida".
CREATE TABLE counter_sale_settings (
 tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE RESTRICT,
 staff_discount_limit NUMERIC NOT NULL DEFAULT 0
  CHECK (staff_discount_limit >= 0 AND staff_discount_limit <= 100 AND scale(staff_discount_limit) <= 2),
 pix_account_id UUID,
 debit_fee_percent NUMERIC
  CHECK (debit_fee_percent IS NULL OR (debit_fee_percent >= 0 AND debit_fee_percent < 100
   AND scale(debit_fee_percent) <= 2)),
 debit_release_days SMALLINT CHECK (debit_release_days IS NULL OR debit_release_days BETWEEN 0 AND 120),
 credit_fee_percent NUMERIC
  CHECK (credit_fee_percent IS NULL OR (credit_fee_percent >= 0 AND credit_fee_percent < 100
   AND scale(credit_fee_percent) <= 2)),
 credit_release_days SMALLINT CHECK (credit_release_days IS NULL OR credit_release_days BETWEEN 0 AND 120),
 updated_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT counter_sale_settings_pix_fk FOREIGN KEY (pix_account_id, tenant_id)
  REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT
);

-- One sale at the counter. Totals are snapshots: subtotal = sum of the items, total = subtotal -
-- discount (> 0). Payment: CASH into the open cash session's drawer, PIX into the Pix account,
-- DEBIT/CREDIT stay receivable until released (expected_release_on, null when unknown), with the
-- informed fee snapshot (null when unknown). Never deleted: the OWNER cancels with a reason,
-- choosing whether the goods come back to stock.
CREATE TABLE counter_sale (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 request_id UUID NOT NULL,
 payment_method VARCHAR(10) NOT NULL CHECK (payment_method IN ('CASH','PIX','DEBIT','CREDIT')),
 subtotal_amount NUMERIC NOT NULL
  CHECK (subtotal_amount > 0 AND subtotal_amount < 1000000000000000 AND scale(subtotal_amount) <= 2),
 discount_amount NUMERIC NOT NULL CHECK (discount_amount >= 0 AND scale(discount_amount) <= 2),
 total_amount NUMERIC NOT NULL CHECK (total_amount > 0 AND scale(total_amount) <= 2),
 fee_percent NUMERIC CHECK (fee_percent IS NULL OR (fee_percent >= 0 AND fee_percent < 100)),
 fee_amount NUMERIC CHECK (fee_amount IS NULL OR (fee_amount >= 0 AND scale(fee_amount) <= 2)),
 account_id UUID,
 cash_session_id UUID,
 expected_release_on DATE,
 status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','CANCELLED')),
 created_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 cancelled_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
 cancelled_at TIMESTAMPTZ,
 cancel_reason VARCHAR(1200)
  CHECK (cancel_reason IS NULL OR char_length(btrim(cancel_reason)) BETWEEN 3 AND 300),
 restocked BOOLEAN,
 CONSTRAINT counter_sale_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT counter_sale_request_unique UNIQUE (tenant_id, request_id),
 CONSTRAINT counter_sale_account_fk FOREIGN KEY (account_id, tenant_id)
  REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT counter_sale_session_fk FOREIGN KEY (cash_session_id, tenant_id)
  REFERENCES cash_session(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT counter_sale_total_check CHECK (total_amount = subtotal_amount - discount_amount),
 CONSTRAINT counter_sale_payment_check CHECK (
  (payment_method = 'CASH') = (cash_session_id IS NOT NULL)
  AND (payment_method IN ('CASH','PIX')) = (account_id IS NOT NULL)
  AND (payment_method IN ('DEBIT','CREDIT') OR (fee_percent IS NULL AND fee_amount IS NULL
   AND expected_release_on IS NULL))
  AND (fee_percent IS NULL) = (fee_amount IS NULL)),
 CONSTRAINT counter_sale_cancel_check CHECK (
  (status = 'CANCELLED') = (cancelled_at IS NOT NULL)
  AND (cancelled_at IS NULL) = (cancelled_by IS NULL)
  AND (cancelled_at IS NULL) = (cancel_reason IS NULL)
  AND (cancelled_at IS NULL) = (restocked IS NULL))
);
CREATE INDEX counter_sale_tenant_created_idx ON counter_sale(tenant_id, created_at DESC);

-- Items: snapshots of the catalog at the sale (name, options, SKU, price, cost).
CREATE TABLE counter_sale_item (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 counter_sale_id UUID NOT NULL,
 variant_id UUID NOT NULL,
 position SMALLINT NOT NULL CHECK (position >= 0),
 product_name_snapshot VARCHAR(480) NOT NULL,
 variant_options_snapshot TEXT NOT NULL,
 sku_snapshot VARCHAR(64) NOT NULL,
 unit_price_snapshot NUMERIC NOT NULL CHECK (unit_price_snapshot > 0 AND scale(unit_price_snapshot) <= 2),
 unit_cost_snapshot NUMERIC CHECK (unit_cost_snapshot IS NULL OR (unit_cost_snapshot >= 0
  AND scale(unit_cost_snapshot) <= 2)),
 quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 9999),
 line_total NUMERIC NOT NULL CHECK (line_total > 0 AND scale(line_total) <= 2),
 CONSTRAINT counter_sale_item_sale_fk FOREIGN KEY (counter_sale_id, tenant_id)
  REFERENCES counter_sale(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT counter_sale_item_variant_fk FOREIGN KEY (variant_id, tenant_id)
  REFERENCES catalog_variant(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT counter_sale_item_position_unique UNIQUE (counter_sale_id, position),
 CONSTRAINT counter_sale_item_variant_unique UNIQUE (counter_sale_id, variant_id),
 CONSTRAINT counter_sale_item_total_check CHECK (line_total = unit_price_snapshot * quantity)
);

-- Stock (type is VARCHAR(16)): COUNTER_SALE (out) and COUNTER_RETURN (back, on a cancellation
-- with restock) by
-- the person who registered or cancelled, pointing at the sale.
ALTER TABLE inventory_movement ADD COLUMN counter_sale_id UUID;
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_counter_sale_fk
 FOREIGN KEY (counter_sale_id, tenant_id) REFERENCES counter_sale(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE inventory_movement DROP CONSTRAINT inventory_movement_type_check;
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_type_check CHECK (
 type IN ('ENTRY','ADJUSTMENT','SALE','SALE_REVERSAL','COUNTER_SALE','COUNTER_RETURN'));
ALTER TABLE inventory_movement DROP CONSTRAINT inventory_movement_delta_check;
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_delta_check CHECK (
 delta <> 0
 AND (type <> 'ENTRY' OR delta > 0)
 AND (type NOT IN ('SALE','COUNTER_SALE') OR delta < 0)
 AND (type NOT IN ('SALE_REVERSAL','COUNTER_RETURN') OR delta > 0));
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_counter_sale_check CHECK (
 (type IN ('COUNTER_SALE','COUNTER_RETURN')) = (counter_sale_id IS NOT NULL)
 AND (type NOT IN ('COUNTER_SALE','COUNTER_RETURN') OR actor_kind = 'USER'));
CREATE INDEX inventory_movement_counter_sale_idx ON inventory_movement(counter_sale_id)
 WHERE counter_sale_id IS NOT NULL;

-- Money: COUNTER_SALE (in: cash into the drawer's session, Pix into its account) and
-- COUNTER_SALE_REFUND (out, on a cancellation), pointing at the sale.
ALTER TABLE account_movement ADD COLUMN counter_sale_id UUID;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_counter_sale_fk
 FOREIGN KEY (counter_sale_id, tenant_id) REFERENCES counter_sale(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE account_movement DROP CONSTRAINT account_movement_kind_check;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_kind_check CHECK (
 kind IN ('ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','EXPENSE_PAYMENT','EXPENSE_REFUND',
  'CASH_DIFFERENCE','OWNER_WITHDRAWAL','OWNER_CONTRIBUTION','COUNTER_SALE','COUNTER_SALE_REFUND'));
ALTER TABLE account_movement ADD CONSTRAINT account_movement_counter_sale_check CHECK (
 (kind IN ('COUNTER_SALE','COUNTER_SALE_REFUND')) = (counter_sale_id IS NOT NULL)
 AND (kind <> 'COUNTER_SALE' OR amount > 0)
 AND (kind <> 'COUNTER_SALE_REFUND' OR amount < 0));
CREATE INDEX account_movement_counter_sale_idx ON account_movement(counter_sale_id)
 WHERE counter_sale_id IS NOT NULL;

-- Ledger (C7): sale facts point at an online order or at a counter sale, exactly one of them.
ALTER TABLE finance_entry ADD COLUMN counter_sale_id UUID;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_counter_sale_fk
 FOREIGN KEY (counter_sale_id, tenant_id) REFERENCES counter_sale(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE finance_entry DROP CONSTRAINT finance_entry_order_check;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_order_check CHECK (
 (type IN ('SALE','REFUND','FEE','COGS','COGS_REVERSAL'))
  = (order_id IS NOT NULL OR counter_sale_id IS NOT NULL)
 AND NOT (order_id IS NOT NULL AND counter_sale_id IS NOT NULL));
CREATE INDEX finance_entry_counter_sale_idx ON finance_entry(counter_sale_id)
 WHERE counter_sale_id IS NOT NULL;

-- The append-only trigger (V27, V43) also keeps the new column from changing.
CREATE OR REPLACE FUNCTION finance_entry_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'finance_entry is append-only: % is not allowed', TG_OP
   USING ERRCODE = 'restrict_violation';
 END IF;
 IF OLD.settled_at IS NULL AND NEW.settled_at IS NOT NULL
    AND (NEW.id, NEW.tenant_id, NEW.entry_key, NEW.type, NEW.amount, NEW.currency,
         NEW.competence_at, NEW.order_id, NEW.cost_incomplete, NEW.created_at, NEW.expense_id,
         NEW.cash_session_id, NEW.counter_sale_id)
    IS NOT DISTINCT FROM
        (OLD.id, OLD.tenant_id, OLD.entry_key, OLD.type, OLD.amount, OLD.currency,
         OLD.competence_at, OLD.order_id, OLD.cost_incomplete, OLD.created_at, OLD.expense_id,
         OLD.cash_session_id, OLD.counter_sale_id)
 THEN
  RETURN NEW;
 END IF;
 RAISE EXCEPTION 'finance_entry is append-only: only settled_at may be filled, once'
  USING ERRCODE = 'restrict_violation';
END $$;
