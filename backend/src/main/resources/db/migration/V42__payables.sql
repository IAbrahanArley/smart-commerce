-- SC-051 (ADR-0019 C3, C4): expenses become payables. Additive: new expense columns, filled from
-- the existing ones, and two movement kinds that tie a payment to its account. No ledger entry
-- and no movement changes.

-- due_date: when the bill is due; existing expenses fall due on their competence date.
-- Installments of one bill share installment_group (the request that created them); each one
-- counts in the month it falls due. A monthly bill points its successor at itself
-- (recurrence_source_id), created once when it is paid.
-- Payment: paid_on as before, plus the account it left (paid_account_id), interest or fine
-- (surcharge_amount), discount, and who recorded it and when. Expenses paid before this
-- migration keep paid_on with no account ("conta não informada").
ALTER TABLE expense
 ADD COLUMN due_date DATE,
 ADD COLUMN supplier VARCHAR(480)
  CHECK (supplier IS NULL OR char_length(btrim(supplier)) BETWEEN 2 AND 120),
 ADD COLUMN document_number VARCHAR(160)
  CHECK (document_number IS NULL OR char_length(btrim(document_number)) BETWEEN 1 AND 40),
 ADD COLUMN installment_group UUID,
 ADD COLUMN installment_number SMALLINT,
 ADD COLUMN installment_count SMALLINT,
 ADD COLUMN recurrence VARCHAR(10) NOT NULL DEFAULT 'NONE'
  CHECK (recurrence IN ('NONE','MONTHLY')),
 ADD COLUMN recurrence_source_id UUID,
 ADD COLUMN paid_account_id UUID,
 ADD COLUMN surcharge_amount NUMERIC NOT NULL DEFAULT 0
  CHECK (surcharge_amount >= 0 AND surcharge_amount < 100000000000000000
   AND scale(surcharge_amount) <= 2),
 ADD COLUMN discount_amount NUMERIC NOT NULL DEFAULT 0
  CHECK (discount_amount >= 0 AND discount_amount < 100000000000000000
   AND scale(discount_amount) <= 2),
 ADD COLUMN paid_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
 ADD COLUMN paid_at TIMESTAMPTZ;

UPDATE expense SET due_date = competence_date;
ALTER TABLE expense ALTER COLUMN due_date SET NOT NULL;

ALTER TABLE expense
 ADD CONSTRAINT expense_installment_check CHECK (
  (installment_group IS NULL AND installment_number IS NULL AND installment_count IS NULL)
  OR (installment_group IS NOT NULL AND installment_count BETWEEN 2 AND 24
   AND installment_number BETWEEN 1 AND installment_count)),
 ADD CONSTRAINT expense_installment_unique UNIQUE (tenant_id, installment_group, installment_number),
 ADD CONSTRAINT expense_recurrence_installment_check
  CHECK (recurrence = 'NONE' OR installment_group IS NULL),
 ADD CONSTRAINT expense_recurrence_source_fk FOREIGN KEY (recurrence_source_id, tenant_id)
  REFERENCES expense(id, tenant_id) ON DELETE RESTRICT,
 ADD CONSTRAINT expense_recurrence_source_unique UNIQUE (tenant_id, recurrence_source_id),
 ADD CONSTRAINT expense_paid_account_fk FOREIGN KEY (paid_account_id, tenant_id)
  REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT,
 -- A payment recorded here has its account, date, author and instant; an old one only its date.
 ADD CONSTRAINT expense_payment_check CHECK (
  (paid_at IS NULL) = (paid_by IS NULL)
  AND (paid_at IS NULL) = (paid_account_id IS NULL)
  AND (paid_at IS NULL OR paid_on IS NOT NULL)
  AND (paid_at IS NOT NULL OR (surcharge_amount = 0 AND discount_amount = 0))
  AND discount_amount < amount + surcharge_amount);

CREATE INDEX expense_tenant_due_idx ON expense(tenant_id, due_date);
CREATE INDEX expense_tenant_open_idx ON expense(tenant_id, due_date)
 WHERE status = 'ACTIVE' AND paid_on IS NULL;

-- The money of a payment leaves its account (EXPENSE_PAYMENT, negative) and, when a paid bill is
-- cancelled, comes back (EXPENSE_REFUND, positive). Both point at the expense.
ALTER TABLE account_movement ADD COLUMN expense_id UUID;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_expense_fk
 FOREIGN KEY (expense_id, tenant_id) REFERENCES expense(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE account_movement DROP CONSTRAINT account_movement_kind_check;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_kind_check CHECK (
 kind IN ('ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','EXPENSE_PAYMENT','EXPENSE_REFUND'));
ALTER TABLE account_movement ADD CONSTRAINT account_movement_expense_check CHECK (
 (kind IN ('EXPENSE_PAYMENT','EXPENSE_REFUND')) = (expense_id IS NOT NULL)
 AND (kind <> 'EXPENSE_PAYMENT' OR amount < 0)
 AND (kind <> 'EXPENSE_REFUND' OR amount > 0));
CREATE INDEX account_movement_expense_idx ON account_movement(expense_id)
 WHERE expense_id IS NOT NULL;
