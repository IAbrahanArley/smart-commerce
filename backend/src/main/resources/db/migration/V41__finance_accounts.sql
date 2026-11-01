-- SC-050 (ADR-0019 C2, C3): where the store's money is. Additive: two new tables and a trigger.
-- finance_entry stays the result by competence; account_movement is the real money (which
-- account, which day, how much). Balance = sum of movements, never a stored field.

-- An account of the store: the cash drawer, a bank account or the payment provider. Accounts are
-- never deleted (their movements point at them). system_key marks the accounts the platform keeps
-- by itself: the default drawer and the Mercado Pago account, one of each per store.
CREATE TABLE finance_account (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 type VARCHAR(20) NOT NULL CHECK (type IN ('CASH','BANK','PAYMENT_PROVIDER')),
 name VARCHAR(240) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 3 AND 60),
 system_key VARCHAR(20) CHECK (system_key IN ('DEFAULT_CASH','MERCADO_PAGO')),
 request_id UUID,
 created_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT finance_account_id_tenant_unique UNIQUE (id, tenant_id),
 CONSTRAINT finance_account_system_unique UNIQUE (tenant_id, system_key),
 CONSTRAINT finance_account_request_unique UNIQUE (tenant_id, request_id),
 -- The provider account is always the platform's; accounts the OWNER creates carry his request.
 CHECK ((type = 'PAYMENT_PROVIDER') = (system_key IS NOT DISTINCT FROM 'MERCADO_PAGO')),
 CHECK (system_key <> 'DEFAULT_CASH' OR type = 'CASH'),
 CHECK ((system_key IS NULL) = (request_id IS NOT NULL AND created_by IS NOT NULL))
);
CREATE INDEX finance_account_tenant_idx ON finance_account(tenant_id, created_at);

-- One signed movement of money in one account. Append-only (trigger below): a correction is a
-- new opposite movement with a reason. movement_key makes a retry the same movement, per store.
-- A transfer is two movements (out, in) written in one transaction and sharing transfer_id.
CREATE TABLE account_movement (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 account_id UUID NOT NULL,
 movement_key VARCHAR(120) NOT NULL,
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN')),
 amount NUMERIC NOT NULL
  CHECK (amount <> 0 AND abs(amount) < 1000000000000000 AND scale(amount) <= 2),
 currency CHAR(3) NOT NULL DEFAULT 'BRL' CHECK (currency = 'BRL'),
 occurred_on DATE NOT NULL,
 description VARCHAR(800) CHECK (description IS NULL OR char_length(btrim(description)) BETWEEN 3 AND 200),
 transfer_id UUID,
 created_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT account_movement_account_fk
  FOREIGN KEY (account_id, tenant_id) REFERENCES finance_account(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT account_movement_key_unique UNIQUE (tenant_id, movement_key),
 CHECK (kind <> 'TRANSFER_OUT' OR amount < 0),
 CHECK (kind <> 'TRANSFER_IN' OR amount > 0),
 CHECK ((kind IN ('TRANSFER_OUT','TRANSFER_IN')) = (transfer_id IS NOT NULL)),
 CHECK (kind <> 'ADJUSTMENT' OR description IS NOT NULL)
);
CREATE INDEX account_movement_account_idx ON account_movement(account_id, occurred_on, created_at);

CREATE FUNCTION account_movement_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'account_movement is append-only: % is not allowed', TG_OP
  USING ERRCODE = 'restrict_violation';
END $$;

CREATE TRIGGER account_movement_append_only
 BEFORE UPDATE OR DELETE ON account_movement
 FOR EACH ROW EXECUTE FUNCTION account_movement_append_only();
