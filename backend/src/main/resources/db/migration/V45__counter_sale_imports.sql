-- SC-054 (ADR-0019 C14): counter sales imported from a spreadsheet. Additive: one new table and
-- three counter_sale columns; the payment CHECK only widens (imported cash has no cash session).

-- One confirmed import: who, when, the file's SHA-256 (never its content) and what it wrote.
-- cash_account_id: where the file's cash sales went (a drawer or a bank account, chosen by the
-- OWNER at confirmation); null when the file had none.
CREATE TABLE counter_sale_import (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 file_hash CHAR(64) NOT NULL CHECK (file_hash ~ '^[0-9a-f]{64}$'),
 item_lines INTEGER NOT NULL CHECK (item_lines BETWEEN 1 AND 500),
 sales_count INTEGER NOT NULL CHECK (sales_count BETWEEN 1 AND 500),
 cash_account_id UUID,
 created_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT counter_sale_import_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT counter_sale_import_cash_fk FOREIGN KEY (cash_account_id, tenant_id)
  REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT
);
CREATE INDEX counter_sale_import_tenant_idx ON counter_sale_import(tenant_id, created_at DESC);

-- An imported sale points at its import, keeps its fingerprint (import_key: date, code, payment
-- and items, unique per store, so the same or a corrected file never writes it twice) and the
-- day it happened (sold_on, its competence). Sales registered live have none of the three.
ALTER TABLE counter_sale
 ADD COLUMN import_id UUID,
 ADD COLUMN import_key CHAR(64) CHECK (import_key IS NULL OR import_key ~ '^[0-9a-f]{64}$'),
 ADD COLUMN sold_on DATE;
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_import_fk
 FOREIGN KEY (import_id, tenant_id) REFERENCES counter_sale_import(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_import_key_unique UNIQUE (tenant_id, import_key);
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_imported_check CHECK (
 (import_id IS NULL) = (import_key IS NULL) AND (import_id IS NULL) = (sold_on IS NULL));
CREATE INDEX counter_sale_import_idx ON counter_sale(import_id) WHERE import_id IS NOT NULL;

-- Live cash sales go into an open cash session; imported ones happened before and go straight
-- into the chosen account.
ALTER TABLE counter_sale DROP CONSTRAINT counter_sale_payment_check;
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_payment_check CHECK (
 (payment_method = 'CASH' AND import_id IS NULL) = (cash_session_id IS NOT NULL)
 AND (payment_method IN ('CASH','PIX')) = (account_id IS NOT NULL)
 AND (payment_method IN ('DEBIT','CREDIT') OR (fee_percent IS NULL AND fee_amount IS NULL
  AND expected_release_on IS NULL))
 AND (fee_percent IS NULL) = (fee_amount IS NULL));
