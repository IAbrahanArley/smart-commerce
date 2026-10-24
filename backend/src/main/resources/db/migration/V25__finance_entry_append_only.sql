-- SC-023 (ADR-0015 F7): the ledger is append-only by design; this makes the database enforce it
-- too (defense in depth). Additive: one function and one trigger; no row changes.
-- Allowed: INSERT (the application already uses ON CONFLICT DO NOTHING) and filling settled_at
-- once, from NULL, when the provider releases the money (SC-018). Anything else, including
-- DELETE, raises. TRUNCATE (disposable test databases only) is not a row operation and is
-- unaffected.

CREATE FUNCTION finance_entry_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'finance_entry is append-only: % is not allowed', TG_OP
   USING ERRCODE = 'restrict_violation';
 END IF;
 IF OLD.settled_at IS NULL AND NEW.settled_at IS NOT NULL
    AND (NEW.id, NEW.tenant_id, NEW.entry_key, NEW.type, NEW.amount, NEW.currency,
         NEW.competence_at, NEW.order_id, NEW.cost_incomplete, NEW.created_at, NEW.expense_id)
    IS NOT DISTINCT FROM
        (OLD.id, OLD.tenant_id, OLD.entry_key, OLD.type, OLD.amount, OLD.currency,
         OLD.competence_at, OLD.order_id, OLD.cost_incomplete, OLD.created_at, OLD.expense_id)
 THEN
  RETURN NEW;
 END IF;
 RAISE EXCEPTION 'finance_entry is append-only: only settled_at may be filled, once'
  USING ERRCODE = 'restrict_violation';
END $$;

CREATE TRIGGER finance_entry_append_only
 BEFORE UPDATE OR DELETE ON finance_entry
 FOR EACH ROW EXECUTE FUNCTION finance_entry_append_only();
