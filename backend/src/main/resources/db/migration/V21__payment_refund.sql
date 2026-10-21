-- SC-016 (ADR-0013 P9): controlled full refund. Additive: a new table and CHECKs widened to admit
-- REFUNDED. Existing rows keep their meaning.

-- REFUNDED comes only after PAID, so it keeps paid_at and any earlier expiry or cancellation.
ALTER TABLE sales_order DROP CONSTRAINT sales_order_status_check;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_status_check
 CHECK (status IN ('PENDING_PAYMENT','EXPIRED','CANCELLED','PAID','REFUNDED'));
ALTER TABLE sales_order ADD COLUMN refunded_at TIMESTAMPTZ;
ALTER TABLE sales_order DROP CONSTRAINT sales_order_paid_check;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_paid_check CHECK (
 (status IN ('PAID','REFUNDED')) = (paid_at IS NOT NULL)
 AND (status = 'REFUNDED') = (refunded_at IS NOT NULL)
 AND (NOT paid_without_stock OR status IN ('PAID','REFUNDED')));
ALTER TABLE sales_order DROP CONSTRAINT sales_order_cancellation_check;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_cancellation_check CHECK (
 (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
 AND (cancelled_at IS NULL OR status IN ('CANCELLED','PAID','REFUNDED'))
 AND (cancelled_at IS NULL) = (cancelled_by IS NULL)
 AND (cancelled_at IS NULL) = (cancel_reason IS NULL)
 AND (expired_at IS NULL OR status IN ('EXPIRED','PAID','REFUNDED')));

-- V19 named the CHECKs it replaced, so they can be dropped by name.
ALTER TABLE payment DROP CONSTRAINT payment_status_check;
ALTER TABLE payment ADD CONSTRAINT payment_status_check
 CHECK (status IN ('CREATING','CREATED','APPROVED','REFUNDED'));
ALTER TABLE payment DROP CONSTRAINT payment_created_check;
ALTER TABLE payment ADD CONSTRAINT payment_created_check CHECK (
 (status IN ('CREATED','APPROVED','REFUNDED')) = (provider_order_id IS NOT NULL)
 AND (provider_order_id IS NULL) = (provider_user_id IS NULL)
 AND (provider_order_id IS NULL) = (checkout_url IS NULL)
 AND (provider_order_id IS NULL) = (provider_expires_at IS NULL)
 AND (status IN ('APPROVED','REFUNDED')) = (approved_at IS NOT NULL)
 AND (status NOT IN ('APPROVED','REFUNDED') OR paid_amount = amount));
-- Approved payments waiting for a refund confirmation are reconciled too.
CREATE INDEX payment_refund_due_idx ON payment(next_check_at) WHERE status = 'APPROVED';

-- One full refund per payment (P9): who asked, why, whether the units go back to stock, and
-- whether the provider has confirmed it. REQUESTED is retried with the same idempotency key.
CREATE TABLE payment_refund (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 payment_id UUID NOT NULL UNIQUE REFERENCES payment(id) ON DELETE RESTRICT,
 order_id UUID NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','CONFIRMED')),
 amount NUMERIC NOT NULL CHECK (amount > 0 AND scale(amount) <= 2),
 reason VARCHAR(1200) NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 3 AND 300),
 restock BOOLEAN NOT NULL,
 requested_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 requested_at TIMESTAMPTZ NOT NULL,
 provider_accepted_at TIMESTAMPTZ,
 last_error VARCHAR(40),
 confirmed_at TIMESTAMPTZ,
 CONSTRAINT payment_refund_order_tenant_fk FOREIGN KEY (order_id, tenant_id)
 REFERENCES sales_order(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT payment_refund_confirmed_check CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);
CREATE INDEX payment_refund_tenant_idx ON payment_refund(tenant_id);
