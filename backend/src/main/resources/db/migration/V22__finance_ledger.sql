-- SC-019 (ADR-0015 F1, F4, F7, F10): append-only finance ledger. Additive: one new table, an index
-- and the initial entries of orders already paid or refunded. No existing row changes.

-- One row per financial fact, signed (income positive, cost and deduction negative), in BRL.
-- entry_key is derived from the fact, so recording it again is a no-op (idempotent).
-- competence_at: when the fact counts (F1); settled_at: when money moves (F3, filled by SC-018).
-- cost_incomplete: a COGS entry built from items of which some had no cost (F4: margin
-- unavailable, never presumed).
CREATE TABLE finance_entry (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
 entry_key VARCHAR(120) NOT NULL,
 type VARCHAR(20) NOT NULL CHECK (type IN ('SALE','REFUND','FEE','COGS','COGS_REVERSAL','EXPENSE')),
 amount NUMERIC NOT NULL CHECK (scale(amount) <= 2 AND amount > -100000000000000000
  AND amount < 100000000000000000),
 currency VARCHAR(3) NOT NULL CHECK (currency = 'BRL'),
 competence_at TIMESTAMPTZ NOT NULL,
 settled_at TIMESTAMPTZ,
 order_id UUID,
 cost_incomplete BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT finance_entry_key_unique UNIQUE (tenant_id, entry_key),
 CONSTRAINT finance_entry_order_tenant_fk FOREIGN KEY (order_id, tenant_id)
 REFERENCES sales_order(id, tenant_id) ON DELETE RESTRICT,
 CONSTRAINT finance_entry_sign_check CHECK (
  (type IN ('SALE','COGS_REVERSAL') AND amount >= 0)
  OR (type IN ('REFUND','FEE','COGS','EXPENSE') AND amount <= 0)),
 CONSTRAINT finance_entry_order_check CHECK (
  (type IN ('SALE','REFUND','FEE','COGS','COGS_REVERSAL')) = (order_id IS NOT NULL)),
 CONSTRAINT finance_entry_cost_check CHECK (NOT cost_incomplete OR type IN ('COGS','COGS_REVERSAL'))
);
CREATE INDEX finance_entry_competence_idx ON finance_entry(tenant_id, competence_at);
CREATE INDEX finance_entry_order_idx ON finance_entry(order_id) WHERE order_id IS NOT NULL;

-- Initial entries (F7) for orders paid before the ledger existed, with the same keys the code
-- uses, so a later write of the same fact is a no-op. Sale and COGS at paid_at; refund and, when
-- the goods came back or never left, the COGS reversal at refunded_at.
INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,order_id,created_at)
SELECT gen_random_uuid(), o.tenant_id, 'sale:' || o.id, 'SALE', o.total_amount, 'BRL', o.paid_at, o.id, now()
FROM sales_order o WHERE o.status IN ('PAID','REFUNDED')
ON CONFLICT (tenant_id, entry_key) DO NOTHING;

INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,order_id,
  cost_incomplete,created_at)
SELECT gen_random_uuid(), o.tenant_id, 'cogs:' || o.id, 'COGS',
  -COALESCE(SUM(i.unit_cost_snapshot * i.quantity), 0), 'BRL', o.paid_at, o.id,
  bool_or(i.unit_cost_snapshot IS NULL), now()
FROM sales_order o JOIN sales_order_item i ON i.order_id = o.id AND i.tenant_id = o.tenant_id
WHERE o.status IN ('PAID','REFUNDED')
GROUP BY o.id, o.tenant_id, o.paid_at
ON CONFLICT (tenant_id, entry_key) DO NOTHING;

INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,order_id,created_at)
SELECT gen_random_uuid(), o.tenant_id, 'refund:' || o.id, 'REFUND', -o.total_amount, 'BRL',
  o.refunded_at, o.id, now()
FROM sales_order o WHERE o.status = 'REFUNDED'
ON CONFLICT (tenant_id, entry_key) DO NOTHING;

INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,order_id,
  cost_incomplete,created_at)
SELECT gen_random_uuid(), c.tenant_id, 'cogs-reversal:' || c.order_id, 'COGS_REVERSAL', -c.amount,
  'BRL', o.refunded_at, c.order_id, c.cost_incomplete, now()
FROM finance_entry c
JOIN sales_order o ON o.id = c.order_id AND o.tenant_id = c.tenant_id
LEFT JOIN payment_refund r ON r.order_id = o.id AND r.tenant_id = o.tenant_id
WHERE c.type = 'COGS' AND o.status = 'REFUNDED' AND (o.paid_without_stock OR r.restock)
ON CONFLICT (tenant_id, entry_key) DO NOTHING;
