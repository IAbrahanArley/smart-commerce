-- ADR-0013, change of P1 (2026-10-24): Checkout Pro through preferences instead of the Orders API.
-- Additive: the preference id takes the place of the provider order id (same column and format);
-- the approved payment's id is kept so the full refund can be requested on it (P9).
-- payment_event keeps its columns: for the "payment" topic, provider_order_id holds the notified
-- payment id (digits, accepted by the existing CHECK) and the tenant is resolved from the seller.

ALTER TABLE payment
 ADD COLUMN provider_payment_id VARCHAR(40)
  CHECK (provider_payment_id IS NULL OR provider_payment_id ~ '^[0-9]{1,40}$');
CREATE UNIQUE INDEX payment_provider_payment_unique
 ON payment(provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
