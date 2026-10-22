-- SC-020 (ADR-0015 F6, F7): manual expenses. Additive: a new table, two ledger columns and the
-- ledger CHECKs widened to admit EXPENSE_REVERSAL. Existing entries keep their meaning.

-- One expense recorded by the store OWNER. Never deleted: a correction cancels it with a reason
-- (who, when, why) and records a new one. Dates are calendar days in America/Sao_Paulo.
-- request_id makes a repeated submission (double click, retry) the same expense.
CREATE TABLE expense (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 request_id UUID NOT NULL,
 category VARCHAR(20) NOT NULL
  CHECK (category IN ('SHIPPING','PACKAGING','MARKETING','RENT','PEOPLE','OTHER')),
 kind VARCHAR(10) NOT NULL CHECK (kind IN ('FIXED','VARIABLE')),
 description VARCHAR(600) NOT NULL CHECK (char_length(btrim(description)) BETWEEN 3 AND 200),
 amount NUMERIC NOT NULL
  CHECK (amount > 0 AND amount < 100000000000000000 AND scale(amount) <= 2),
 competence_date DATE NOT NULL,
 paid_on DATE,
 status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','CANCELLED')),
 created_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 cancelled_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
 cancelled_at TIMESTAMPTZ,
 cancel_reason VARCHAR(1200)
  CHECK (cancel_reason IS NULL OR char_length(btrim(cancel_reason)) BETWEEN 3 AND 300),
 CONSTRAINT expense_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT expense_tenant_request_unique UNIQUE (tenant_id, request_id),
 CONSTRAINT expense_cancellation_check CHECK (
  (status = 'CANCELLED') = (cancelled_at IS NOT NULL)
  AND (cancelled_at IS NULL) = (cancelled_by IS NULL)
  AND (cancelled_at IS NULL) = (cancel_reason IS NULL))
);
CREATE INDEX expense_tenant_competence_idx ON expense(tenant_id, competence_date DESC);

-- The ledger points at the expense of EXPENSE and EXPENSE_REVERSAL entries. A cancellation adds
-- the reversal (same competence and settlement as the original), so periods stay right without
-- touching the original entry.
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

ALTER TABLE finance_entry ADD COLUMN expense_id UUID;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_expense_tenant_fk
 FOREIGN KEY (expense_id, tenant_id) REFERENCES expense(id, tenant_id) ON DELETE RESTRICT;
-- Only the type list has COGS_REVERSAL right before EXPENSE.
SELECT sc_drop_check('finance_entry', '''COGS_REVERSAL''::character varying, ''EXPENSE''');
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_type_check CHECK (
 type IN ('SALE','REFUND','FEE','COGS','COGS_REVERSAL','EXPENSE','EXPENSE_REVERSAL'));
ALTER TABLE finance_entry DROP CONSTRAINT finance_entry_sign_check;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_sign_check CHECK (
 (type IN ('SALE','COGS_REVERSAL','EXPENSE_REVERSAL') AND amount >= 0)
 OR (type IN ('REFUND','FEE','COGS','EXPENSE') AND amount <= 0));
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_expense_check CHECK (
 (type IN ('EXPENSE','EXPENSE_REVERSAL')) = (expense_id IS NOT NULL));
CREATE INDEX finance_entry_expense_idx ON finance_entry(expense_id) WHERE expense_id IS NOT NULL;

DROP FUNCTION sc_drop_check(regclass, text);
