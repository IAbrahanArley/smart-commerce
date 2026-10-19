-- SC-010 (ADR-0012, decisões da SC-010): expiration, cancellation by the store OWNER and D2
-- anonymization. Additive for existing rows: new nullable columns, buyer columns that may become
-- NULL only after anonymization, and the address rule rewritten to allow that state. No data is
-- changed by this migration.

ALTER TABLE sales_order
 ADD COLUMN expired_at TIMESTAMPTZ,
 ADD COLUMN cancelled_at TIMESTAMPTZ,
 ADD COLUMN cancelled_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
 ADD COLUMN cancel_reason VARCHAR(1200)
  CHECK (cancel_reason IS NULL OR char_length(btrim(cancel_reason)) BETWEEN 3 AND 300),
 ADD COLUMN anonymized_at TIMESTAMPTZ;

-- Cancellation data exists exactly for cancelled orders; expired_at only for expired ones.
ALTER TABLE sales_order ADD CONSTRAINT sales_order_cancellation_check CHECK (
 (status = 'CANCELLED') = (cancelled_at IS NOT NULL)
 AND (cancelled_at IS NULL) = (cancelled_by IS NULL)
 AND (cancelled_at IS NULL) = (cancel_reason IS NULL)
 AND (expired_at IS NULL OR status = 'EXPIRED'));

-- D2: buyer contact data may be removed only from closed (expired or cancelled) orders, and then
-- all of it at once.
ALTER TABLE sales_order
 ALTER COLUMN buyer_name DROP NOT NULL,
 ALTER COLUMN buyer_email DROP NOT NULL,
 ALTER COLUMN buyer_email_key DROP NOT NULL,
 ALTER COLUMN buyer_phone DROP NOT NULL;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_anonymization_check CHECK (
 (anonymized_at IS NULL
   AND buyer_name IS NOT NULL AND buyer_email IS NOT NULL
   AND buyer_email_key IS NOT NULL AND buyer_phone IS NOT NULL)
 OR (anonymized_at IS NOT NULL AND status IN ('EXPIRED','CANCELLED')
   AND buyer_name IS NULL AND buyer_email IS NULL AND buyer_email_key IS NULL AND buyer_phone IS NULL
   AND address_line1 IS NULL AND address_line2 IS NULL AND address_district IS NULL
   AND address_city IS NULL AND address_state IS NULL AND address_postal_code IS NULL));

-- Same address rule as V13 while the order keeps its personal data; anonymized orders keep none.
ALTER TABLE sales_order DROP CONSTRAINT sales_order_address_check;
ALTER TABLE sales_order ADD CONSTRAINT sales_order_address_check CHECK (
 anonymized_at IS NOT NULL OR (
  (fulfillment_type = 'FIXED_SHIPPING') = (address_line1 IS NOT NULL)
  AND (address_line1 IS NULL) = (address_district IS NULL)
  AND (address_line1 IS NULL) = (address_city IS NULL)
  AND (address_line1 IS NULL) = (address_state IS NULL)
  AND (address_line1 IS NULL) = (address_postal_code IS NULL)
  AND (address_line1 IS NOT NULL OR address_line2 IS NULL)));

-- Expiration batch: pending orders by deadline.
CREATE INDEX sales_order_pending_due_idx ON sales_order(reservation_expires_at)
 WHERE status = 'PENDING_PAYMENT';
-- Anonymization batch: closed orders still holding personal data, oldest first.
CREATE INDEX sales_order_anonymization_due_idx ON sales_order(created_at)
 WHERE anonymized_at IS NULL AND status IN ('EXPIRED','CANCELLED');
-- Admin list filtered by status, newest number first (the unfiltered list uses the UNIQUE
-- (store_id, number) index).
CREATE INDEX sales_order_store_status_number_idx ON sales_order(store_id, status, number DESC);
