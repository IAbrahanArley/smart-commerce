-- SC-052 (ADR-0019 C5): cash register sessions on the store's drawers. Additive: one new table,
-- new movement kinds and one new ledger type. No existing row changes.

-- One session of one drawer (a CASH finance_account): opened with the counted change float,
-- closed with a blind count. A drawer has at most one OPEN session (partial unique index); a
-- closed session never changes again (trigger below). Reopening creates a new session that
-- points at the closed one, with the OWNER's reason.
-- expected_amount: the drawer balance right before the closing difference; only the OWNER sees it.
CREATE TABLE cash_session (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 account_id UUID NOT NULL,
 status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN','CLOSED')),
 request_id UUID NOT NULL,
 opening_amount NUMERIC NOT NULL
  CHECK (opening_amount >= 0 AND opening_amount < 1000000000000000 AND scale(opening_amount) <= 2),
 opened_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 opened_at TIMESTAMPTZ NOT NULL,
 reopened_from UUID,
 reopen_reason VARCHAR(1200)
  CHECK (reopen_reason IS NULL OR char_length(btrim(reopen_reason)) BETWEEN 3 AND 300),
 close_request_id UUID,
 counted_amount NUMERIC
  CHECK (counted_amount >= 0 AND counted_amount < 1000000000000000 AND scale(counted_amount) <= 2),
 expected_amount NUMERIC CHECK (scale(expected_amount) <= 2),
 closed_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
 closed_at TIMESTAMPTZ,
 CONSTRAINT cash_session_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT cash_session_request_unique UNIQUE (tenant_id, request_id),
 CONSTRAINT cash_session_account_fk FOREIGN KEY (account_id, tenant_id)
  REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT cash_session_reopened_fk FOREIGN KEY (reopened_from, tenant_id)
  REFERENCES cash_session(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT cash_session_reopened_unique UNIQUE (tenant_id, reopened_from),
 CONSTRAINT cash_session_reopen_check CHECK ((reopened_from IS NULL) = (reopen_reason IS NULL)),
 CONSTRAINT cash_session_close_check CHECK (
  (status = 'CLOSED') = (closed_at IS NOT NULL)
  AND (closed_at IS NULL) = (closed_by IS NULL)
  AND (closed_at IS NULL) = (close_request_id IS NULL)
  AND (closed_at IS NULL) = (counted_amount IS NULL)
  AND (closed_at IS NULL) = (expected_amount IS NULL))
);
CREATE UNIQUE INDEX cash_session_one_open_idx ON cash_session(account_id) WHERE status = 'OPEN';
CREATE INDEX cash_session_tenant_idx ON cash_session(tenant_id, opened_at DESC);

-- Only the OPEN -> CLOSED transition, once, with the opening data untouched; never a delete.
CREATE FUNCTION cash_session_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' OR OLD.status <> 'OPEN' OR NEW.status <> 'CLOSED'
    OR (NEW.id, NEW.tenant_id, NEW.account_id, NEW.request_id, NEW.opening_amount, NEW.opened_by,
        NEW.opened_at, NEW.reopened_from, NEW.reopen_reason)
       IS DISTINCT FROM
       (OLD.id, OLD.tenant_id, OLD.account_id, OLD.request_id, OLD.opening_amount, OLD.opened_by,
        OLD.opened_at, OLD.reopened_from, OLD.reopen_reason)
 THEN
  RAISE EXCEPTION 'cash_session only closes once: % is not allowed', TG_OP
   USING ERRCODE = 'restrict_violation';
 END IF;
 RETURN NEW;
END $$;

CREATE TRIGGER cash_session_guard
 BEFORE UPDATE OR DELETE ON cash_session
 FOR EACH ROW EXECUTE FUNCTION cash_session_guard();

-- Movements of a session carry it. New kinds:
-- CASH_DIFFERENCE (counted - expected, either sign; also an opening count that differs),
-- OWNER_WITHDRAWAL (negative: a cash drop the owner takes away, not an expense) and
-- OWNER_CONTRIBUTION (positive: a top-up the owner brings, not revenue).
-- A drop or top-up between accounts stays a TRANSFER_OUT/TRANSFER_IN pair.
ALTER TABLE account_movement ADD COLUMN cash_session_id UUID;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_session_fk
 FOREIGN KEY (cash_session_id, tenant_id) REFERENCES cash_session(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE account_movement DROP CONSTRAINT account_movement_kind_check;
ALTER TABLE account_movement ADD CONSTRAINT account_movement_kind_check CHECK (
 kind IN ('ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','EXPENSE_PAYMENT','EXPENSE_REFUND',
  'CASH_DIFFERENCE','OWNER_WITHDRAWAL','OWNER_CONTRIBUTION'));
ALTER TABLE account_movement ADD CONSTRAINT account_movement_session_kind_check CHECK (
 (kind NOT IN ('CASH_DIFFERENCE','OWNER_WITHDRAWAL','OWNER_CONTRIBUTION')
   OR (cash_session_id IS NOT NULL AND description IS NOT NULL))
 AND (kind <> 'OWNER_WITHDRAWAL' OR amount < 0)
 AND (kind <> 'OWNER_CONTRIBUTION' OR amount > 0));
CREATE INDEX account_movement_session_idx ON account_movement(cash_session_id)
 WHERE cash_session_id IS NOT NULL;

-- A session's movement goes to its own drawer, and only while the session is open (the closing
-- difference is written before the session closes, in the same transaction).
CREATE FUNCTION account_movement_session_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.cash_session_id IS NOT NULL AND NOT EXISTS (
   SELECT 1 FROM cash_session s
   WHERE s.id = NEW.cash_session_id AND s.tenant_id = NEW.tenant_id
     AND s.account_id = NEW.account_id AND s.status = 'OPEN') THEN
  RAISE EXCEPTION 'cash session closed or of another drawer'
   USING ERRCODE = 'restrict_violation';
 END IF;
 RETURN NEW;
END $$;

CREATE TRIGGER account_movement_session_guard
 BEFORE INSERT ON account_movement
 FOR EACH ROW EXECUTE FUNCTION account_movement_session_guard();

-- The ledger records a cash difference as its own result line (decided on 2026-11-03): signed,
-- tied to its session, competence and settlement on the closing day.
ALTER TABLE finance_entry ADD COLUMN cash_session_id UUID;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_session_fk
 FOREIGN KEY (cash_session_id, tenant_id) REFERENCES cash_session(id, tenant_id) ON DELETE RESTRICT;
ALTER TABLE finance_entry DROP CONSTRAINT finance_entry_type_check;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_type_check CHECK (
 type IN ('SALE','REFUND','FEE','COGS','COGS_REVERSAL','EXPENSE','EXPENSE_REVERSAL',
  'CASH_DIFFERENCE'));
ALTER TABLE finance_entry DROP CONSTRAINT finance_entry_sign_check;
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_sign_check CHECK (
 (type IN ('SALE','COGS_REVERSAL','EXPENSE_REVERSAL') AND amount >= 0)
 OR (type IN ('REFUND','FEE','COGS','EXPENSE') AND amount <= 0)
 OR (type = 'CASH_DIFFERENCE' AND amount <> 0));
ALTER TABLE finance_entry ADD CONSTRAINT finance_entry_session_check CHECK (
 (type = 'CASH_DIFFERENCE') = (cash_session_id IS NOT NULL));

-- The append-only trigger (V27) also keeps the new column from changing.
CREATE OR REPLACE FUNCTION finance_entry_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'finance_entry is append-only: % is not allowed', TG_OP
   USING ERRCODE = 'restrict_violation';
 END IF;
 IF OLD.settled_at IS NULL AND NEW.settled_at IS NOT NULL
    AND (NEW.id, NEW.tenant_id, NEW.entry_key, NEW.type, NEW.amount, NEW.currency,
         NEW.competence_at, NEW.order_id, NEW.cost_incomplete, NEW.created_at, NEW.expense_id,
         NEW.cash_session_id)
    IS NOT DISTINCT FROM
        (OLD.id, OLD.tenant_id, OLD.entry_key, OLD.type, OLD.amount, OLD.currency,
         OLD.competence_at, OLD.order_id, OLD.cost_incomplete, OLD.created_at, OLD.expense_id,
         OLD.cash_session_id)
 THEN
  RETURN NEW;
 END IF;
 RAISE EXCEPTION 'finance_entry is append-only: only settled_at may be filled, once'
  USING ERRCODE = 'restrict_violation';
END $$;
