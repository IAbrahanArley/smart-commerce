-- SC-014 (ADR-0013 P4, P5, P7, P8): payment confirmation, stock deduction and provider events.
-- Additive: new columns with defaults or NULL, a new table, and CHECKs widened to admit the new
-- states. No existing row changes meaning; every existing order and movement stays valid.

-- Inline CHECKs from earlier migrations have generated names; drop them by definition instead.
CREATE FUNCTION sc_drop_check(p_table regclass, p_fragment text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE c record; dropped integer := 0;
BEGIN
 FOR c IN SELECT conname FROM pg_constraint
  WHERE conrelid = p_table AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%' || p_fragment || '%'
 LOOP
  EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', p_table, c.conname);
  dropped := dropped + 1;
 END LOOP;
 IF dropped <> 1 THEN
  RAISE EXCEPTION 'Expected exactly one CHECK on % matching %, found %', p_table, p_fragment, dropped;
 END IF;
END $$;

-- P8: PAID, also reached from EXPIRED or CANCELLED when the payment arrives late (P4). The
-- expiration and cancellation data stay as history, so their CHECKs admit a later PAID.
SELECT sc_drop_check('sales_order', '''PENDING_PAYMENT''::character varying, ''EXPIRED''');
ALTER TABLE sales_order ADD CONSTRAINT sales_order_status_check
 CHECK (status IN ('PENDING_PAYMENT','EXPIRED','CANCELLED','PAID'));
ALTER TABLE sales_order
 ADD COLUMN paid_at TIMESTAMPTZ,
 -- P4: paid after the reservation ended and the stock could not be reserved again.
 ADD COLUMN paid_without_stock BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_paid_check CHECK (
 (status = 'PAID') = (paid_at IS NOT NULL)
 AND (NOT paid_without_stock OR status = 'PAID'));
ALTER TABLE sales_order DROP CONSTRAINT sales_order_cancellation_check;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_cancellation_check CHECK (
 (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
 AND (cancelled_at IS NULL OR status IN ('CANCELLED','PAID'))
 AND (cancelled_at IS NULL) = (cancelled_by IS NULL)
 AND (cancelled_at IS NULL) = (cancel_reason IS NULL)
 AND (expired_at IS NULL OR status IN ('EXPIRED','PAID')));
-- Paid orders are kept for the store; D2 anonymization still applies only to EXPIRED/CANCELLED
-- (sales_order_anonymization_check is unchanged), so an anonymized order can never become PAID.
CREATE INDEX sales_order_store_paid_idx ON sales_order(store_id, paid_at DESC) WHERE status = 'PAID';

-- APPROVED: the provider order was confirmed paid by the backend's own query (P5).
SELECT sc_drop_check('payment', '''CREATING''::character varying');
ALTER TABLE payment ADD CONSTRAINT payment_status_check
 CHECK (status IN ('CREATING','CREATED','APPROVED'));
ALTER TABLE payment
 ADD COLUMN provider_status_detail VARCHAR(60),
 ADD COLUMN paid_amount NUMERIC
  CHECK (paid_amount IS NULL OR (paid_amount >= 0 AND scale(paid_amount) <= 2)),
 ADD COLUMN approved_at TIMESTAMPTZ,
 ADD COLUMN checked_at TIMESTAMPTZ;
ALTER TABLE payment DROP CONSTRAINT payment_created_check;
ALTER TABLE payment ADD CONSTRAINT payment_created_check CHECK (
 (status IN ('CREATED','APPROVED')) = (provider_order_id IS NOT NULL)
 AND (provider_order_id IS NULL) = (provider_user_id IS NULL)
 AND (provider_order_id IS NULL) = (checkout_url IS NULL)
 AND (provider_order_id IS NULL) = (provider_expires_at IS NULL)
 AND (status = 'APPROVED') = (approved_at IS NOT NULL)
 AND (status <> 'APPROVED' OR paid_amount = amount));

-- Raw provider notifications (P5, P8). Only identifiers and routing data: the body is never
-- stored, so no payer or card data can land here. The event key is unique, so a redelivery is
-- recorded once; processing is asynchronous, retried with backoff.
CREATE TABLE payment_event (
 id UUID PRIMARY KEY,
 provider VARCHAR(20) NOT NULL CHECK (provider = 'MERCADO_PAGO'),
 event_key VARCHAR(160) NOT NULL,
 topic VARCHAR(40) NOT NULL,
 action VARCHAR(60),
 provider_order_id VARCHAR(64) NOT NULL CHECK (provider_order_id ~ '^[A-Za-z0-9_-]{1,64}$'),
 provider_user_id VARCHAR(40) CHECK (provider_user_id ~ '^[0-9]{1,40}$'),
 -- Resolved from the stored payment, never from the notification; NULL when unknown to us.
 tenant_id UUID REFERENCES tenant(id) ON DELETE CASCADE,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PROCESSED','IGNORED','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
 next_attempt_at TIMESTAMPTZ NOT NULL,
 last_error VARCHAR(40),
 received_at TIMESTAMPTZ NOT NULL,
 processed_at TIMESTAMPTZ,
 CONSTRAINT payment_event_key_unique UNIQUE (provider, event_key),
 CONSTRAINT payment_event_processed_check CHECK ((status = 'PENDING') = (processed_at IS NULL))
);
-- Webhook routing matches the provider id without regard to case (the signature admits both).
CREATE INDEX payment_provider_order_lower_idx ON payment (lower(provider_order_id));
CREATE INDEX payment_event_due_idx ON payment_event(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX payment_event_tenant_idx ON payment_event(tenant_id);

-- P7: the ledger records sales by a system actor. USER rows keep their mandatory actor; SYSTEM
-- rows have none. SALE (outbound) and SALE_REVERSAL (back to stock, SC-016) reference the order.
ALTER TABLE inventory_movement
 ADD COLUMN actor_kind VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (actor_kind IN ('USER','SYSTEM')),
 ADD COLUMN order_id UUID;
ALTER TABLE inventory_movement ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_actor_check
 CHECK ((actor_kind = 'USER') = (actor_id IS NOT NULL));
SELECT sc_drop_check('inventory_movement', '''ENTRY''::character varying, ''ADJUSTMENT''');
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_type_check
 CHECK (type IN ('ENTRY','ADJUSTMENT','SALE','SALE_REVERSAL'));
SELECT sc_drop_check('inventory_movement', 'delta <> 0');
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_delta_check CHECK (
 delta <> 0
 AND (type <> 'ENTRY' OR delta > 0)
 AND (type <> 'SALE' OR delta < 0)
 AND (type <> 'SALE_REVERSAL' OR delta > 0));
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_order_check
 CHECK ((type IN ('SALE','SALE_REVERSAL')) = (order_id IS NOT NULL)
  AND (type NOT IN ('SALE','SALE_REVERSAL') OR actor_kind = 'SYSTEM'));
ALTER TABLE inventory_movement ADD CONSTRAINT inventory_movement_order_tenant_fk
 FOREIGN KEY (order_id, tenant_id) REFERENCES sales_order(id, tenant_id) ON DELETE RESTRICT;
CREATE INDEX inventory_movement_order_idx ON inventory_movement(order_id) WHERE order_id IS NOT NULL;

DROP FUNCTION sc_drop_check(regclass, text);
