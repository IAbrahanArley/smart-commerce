-- SC-015 (ADR-0013 P5, P6): minimal reconciliation. Additive: three new columns and an index.
-- The only data written fills the new scheduling column for payments already waiting, so the
-- routine picks them up on its first run.

-- next_check_at: when the routine may query the provider again (claimed with SKIP LOCKED).
-- last_check_error: why the last query did not confirm (provider down, account changed, answer
-- that does not match the stored order), shown to the store as a divergence.
ALTER TABLE payment
 ADD COLUMN next_check_at TIMESTAMPTZ,
 ADD COLUMN check_attempts INTEGER NOT NULL DEFAULT 0 CHECK (check_attempts >= 0),
 ADD COLUMN last_check_error VARCHAR(40);
UPDATE payment SET next_check_at = now() WHERE status = 'CREATED';
CREATE INDEX payment_reconciliation_due_idx ON payment(next_check_at)
 WHERE status = 'CREATED';
