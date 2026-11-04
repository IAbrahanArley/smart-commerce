-- SC-055 (ADR-0019 C9, ADR-0021 R2): the store's customers and store credit ("crediário"). A
-- credit sale at the counter becomes 1 to 12 installments; the customer pays them in parts, into
-- a drawer's open cash session or a bank account. Additive: four new tables, new columns and kinds
-- in counter_sale and account_movement; no existing row changes.

-- The store's customer (ADR-0021 R2): one table for store credit now and for the CRM later. Only
-- what store credit needs (C9): name, phone, optional e-mail, a short note and an optional credit
-- limit. No CPF. Personal data: seen only by the store's OWNER and employees, never logged.
-- Retention: 5 years after the last open balance (C9), pending legal guidance; anonymization is a
-- later task (SC-074).
CREATE TABLE store_customer (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 request_id UUID NOT NULL,
 name VARCHAR(480) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 2 AND 120),
 phone VARCHAR(16) NOT NULL CHECK (phone ~ '^\+?[0-9]{10,15}$'),
 email_original VARCHAR(320),
 email_key VARCHAR(320),
 note VARCHAR(2000) CHECK (note IS NULL OR char_length(btrim(note)) BETWEEN 1 AND 500),
 credit_limit NUMERIC CHECK (credit_limit IS NULL OR (credit_limit >= 0
  AND credit_limit < 1000000000000000 AND scale(credit_limit) <= 2)),
 created_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 updated_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT store_customer_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT store_customer_request_unique UNIQUE (tenant_id, request_id),
 CONSTRAINT store_customer_email_check CHECK ((email_original IS NULL) = (email_key IS NULL))
);
-- ADR-0021 R1: an e-mail, when given, is one customer per store.
CREATE UNIQUE INDEX store_customer_email_unique ON store_customer(tenant_id, email_key)
 WHERE email_key IS NOT NULL;
CREATE INDEX store_customer_tenant_created_idx ON store_customer(tenant_id, created_at DESC, id DESC);

-- A credit sale names its customer and its number of installments; it is never imported (C14).
-- payment_method grows to 16 characters for STORE_CREDIT (no rewrite: varchar widening).
ALTER TABLE counter_sale ALTER COLUMN payment_method TYPE VARCHAR(16);
ALTER TABLE counter_sale DROP CONSTRAINT counter_sale_payment_method_check;
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_payment_method_check CHECK (
 payment_method IN ('CASH','PIX','DEBIT','CREDIT','STORE_CREDIT'));
ALTER TABLE counter_sale
 ADD COLUMN customer_id UUID,
 ADD COLUMN installment_count SMALLINT CHECK (installment_count IS NULL
  OR installment_count BETWEEN 1 AND 12);
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_customer_fk
 FOREIGN KEY (customer_id, tenant_id) REFERENCES store_customer(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE counter_sale ADD CONSTRAINT counter_sale_store_credit_check CHECK (
 (payment_method = 'STORE_CREDIT') = (customer_id IS NOT NULL)
 AND (payment_method = 'STORE_CREDIT') = (installment_count IS NOT NULL)
 AND (payment_method <> 'STORE_CREDIT' OR import_id IS NULL));
CREATE INDEX counter_sale_customer_idx ON counter_sale(tenant_id, customer_id)
 WHERE customer_id IS NOT NULL;

-- The installments of a credit sale, written once with the sale: monthly from the first due date,
-- equal cents with the last one absorbing the rest. Paid by allocations; an installment of a
-- cancelled sale is void.
CREATE TABLE credit_installment (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 counter_sale_id UUID NOT NULL,
 customer_id UUID NOT NULL,
 number SMALLINT NOT NULL CHECK (number BETWEEN 1 AND 12),
 due_on DATE NOT NULL,
 amount NUMERIC NOT NULL
  CHECK (amount > 0 AND amount < 1000000000000000 AND scale(amount) <= 2),
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT credit_installment_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT credit_installment_number_unique UNIQUE (counter_sale_id, number),
 CONSTRAINT credit_installment_sale_fk FOREIGN KEY (counter_sale_id, tenant_id)
  REFERENCES counter_sale(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT credit_installment_customer_fk FOREIGN KEY (customer_id, tenant_id)
  REFERENCES store_customer(id, tenant_id) ON DELETE RESTRICT
);
CREATE INDEX credit_installment_customer_idx ON credit_installment(tenant_id, customer_id, due_on);
CREATE INDEX credit_installment_due_idx ON credit_installment(tenant_id, due_on);

-- A payment of the customer: any amount up to what is open, into a drawer's open cash session or a
-- bank account. It pays the oldest installments first (credit_allocation).
CREATE TABLE credit_receipt (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 request_id UUID NOT NULL,
 customer_id UUID NOT NULL,
 amount NUMERIC NOT NULL
  CHECK (amount > 0 AND amount < 1000000000000000 AND scale(amount) <= 2),
 account_id UUID NOT NULL,
 cash_session_id UUID,
 received_on DATE NOT NULL,
 created_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT credit_receipt_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT credit_receipt_request_unique UNIQUE (tenant_id, request_id),
 CONSTRAINT credit_receipt_customer_fk FOREIGN KEY (customer_id, tenant_id)
  REFERENCES store_customer(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT credit_receipt_account_fk FOREIGN KEY (account_id, tenant_id)
  REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT credit_receipt_session_fk FOREIGN KEY (cash_session_id, tenant_id)
  REFERENCES cash_session(id, tenant_id) ON DELETE RESTRICT
);
CREATE INDEX credit_receipt_customer_idx ON credit_receipt(tenant_id, customer_id, created_at DESC);

-- How much of a receipt went to each installment.
CREATE TABLE credit_allocation (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 receipt_id UUID NOT NULL,
 installment_id UUID NOT NULL,
 amount NUMERIC NOT NULL
  CHECK (amount > 0 AND amount < 1000000000000000 AND scale(amount) <= 2),
 CONSTRAINT credit_allocation_unique UNIQUE (receipt_id, installment_id),
 CONSTRAINT credit_allocation_receipt_fk FOREIGN KEY (receipt_id, tenant_id)
  REFERENCES credit_receipt(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT credit_allocation_installment_fk FOREIGN KEY (installment_id, tenant_id)
  REFERENCES credit_installment(id, tenant_id) ON DELETE RESTRICT
);
CREATE INDEX credit_allocation_installment_idx ON credit_allocation(installment_id);

-- Installments, receipts and allocations are append-only, like the ledger (V27) and the movements
-- (V41): a mistake is corrected by a new fact, never by changing or deleting one.
CREATE FUNCTION store_credit_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
  USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER credit_installment_append_only BEFORE UPDATE OR DELETE ON credit_installment
 FOR EACH ROW EXECUTE FUNCTION store_credit_append_only();
CREATE TRIGGER credit_receipt_append_only BEFORE UPDATE OR DELETE ON credit_receipt
 FOR EACH ROW EXECUTE FUNCTION store_credit_append_only();
CREATE TRIGGER credit_allocation_append_only BEFORE UPDATE OR DELETE ON credit_allocation
 FOR EACH ROW EXECUTE FUNCTION store_credit_append_only();

-- The database, not only the service, refuses paying an installment beyond its amount, paying one
-- of a cancelled sale or of another customer, and cancelling a credit sale that was paid in part.
CREATE FUNCTION credit_allocation_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
 installment credit_installment%ROWTYPE;
BEGIN
 SELECT * INTO installment FROM credit_installment
  WHERE id = NEW.installment_id AND tenant_id = NEW.tenant_id FOR UPDATE;
 IF NOT EXISTS (SELECT 1 FROM counter_sale c WHERE c.id = installment.counter_sale_id
   AND c.tenant_id = NEW.tenant_id AND c.status = 'ACTIVE') THEN
  RAISE EXCEPTION 'credit_allocation: the sale is cancelled' USING ERRCODE = 'check_violation';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM credit_receipt r WHERE r.id = NEW.receipt_id
   AND r.tenant_id = NEW.tenant_id AND r.customer_id = installment.customer_id) THEN
  RAISE EXCEPTION 'credit_allocation: another customer' USING ERRCODE = 'check_violation';
 END IF;
 IF (SELECT COALESCE(SUM(a.amount), 0) FROM credit_allocation a
     WHERE a.installment_id = NEW.installment_id) + NEW.amount > installment.amount THEN
  RAISE EXCEPTION 'credit_allocation: beyond the installment' USING ERRCODE = 'check_violation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER credit_allocation_guard BEFORE INSERT ON credit_allocation
 FOR EACH ROW EXECUTE FUNCTION credit_allocation_guard();

CREATE FUNCTION counter_sale_credit_cancel_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status = 'ACTIVE' AND NEW.status = 'CANCELLED' AND EXISTS (
   SELECT 1 FROM credit_allocation a
   JOIN credit_installment i ON i.id = a.installment_id AND i.tenant_id = a.tenant_id
   WHERE i.counter_sale_id = OLD.id AND i.tenant_id = OLD.tenant_id) THEN
  RAISE EXCEPTION 'counter_sale: a credit sale with receipts cannot be cancelled'
   USING ERRCODE = 'check_violation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER counter_sale_credit_cancel_guard BEFORE UPDATE ON counter_sale
 FOR EACH ROW EXECUTE FUNCTION counter_sale_credit_cancel_guard();

-- An installment belongs to its sale's customer; a receipt goes into a bank account, or into the
-- drawer of the open cash session it names.
CREATE FUNCTION credit_installment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM counter_sale c WHERE c.id = NEW.counter_sale_id
   AND c.tenant_id = NEW.tenant_id AND c.customer_id = NEW.customer_id) THEN
  RAISE EXCEPTION 'credit_installment: not the sale''s customer' USING ERRCODE = 'check_violation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER credit_installment_guard BEFORE INSERT ON credit_installment
 FOR EACH ROW EXECUTE FUNCTION credit_installment_guard();

CREATE FUNCTION credit_receipt_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.cash_session_id IS NULL THEN
  IF NOT EXISTS (SELECT 1 FROM finance_account a WHERE a.id = NEW.account_id
    AND a.tenant_id = NEW.tenant_id AND a.type = 'BANK') THEN
   RAISE EXCEPTION 'credit_receipt: not a bank account' USING ERRCODE = 'check_violation';
  END IF;
 ELSIF NOT EXISTS (SELECT 1 FROM cash_session s WHERE s.id = NEW.cash_session_id
   AND s.tenant_id = NEW.tenant_id AND s.account_id = NEW.account_id AND s.status = 'OPEN') THEN
  RAISE EXCEPTION 'credit_receipt: not the open session''s drawer' USING ERRCODE = 'check_violation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER credit_receipt_guard BEFORE INSERT ON credit_receipt
 FOR EACH ROW EXECUTE FUNCTION credit_receipt_guard();

-- Money: CREDIT_RECEIPT (in) to the receipt's account, tied to the cash session when in a drawer.
ALTER TABLE account_movement ADD COLUMN credit_receipt_id UUID;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_credit_receipt_fk
 FOREIGN KEY (credit_receipt_id, tenant_id) REFERENCES credit_receipt(id, tenant_id)
 ON DELETE RESTRICT;
ALTER TABLE account_movement DROP CONSTRAINT account_movement_kind_check;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_kind_check CHECK (
 kind IN ('ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','EXPENSE_PAYMENT','EXPENSE_REFUND',
  'CASH_DIFFERENCE','OWNER_WITHDRAWAL','OWNER_CONTRIBUTION','COUNTER_SALE','COUNTER_SALE_REFUND',
  'CREDIT_RECEIPT'));
ALTER TABLE account_movement ADD CONSTRAINT account_movement_credit_receipt_check CHECK (
 (kind = 'CREDIT_RECEIPT') = (credit_receipt_id IS NOT NULL)
 AND (kind <> 'CREDIT_RECEIPT' OR amount > 0));
CREATE INDEX account_movement_credit_receipt_idx ON account_movement(credit_receipt_id)
 WHERE credit_receipt_id IS NOT NULL;
