-- SC-025 (ADR-0016 A8, A9): one row per assistant question, metadata only. Never the text of the
-- question or of the answer. Rows older than 90 days are deleted by the advisor housekeeping job.
-- Additive: one new table.

CREATE TABLE advisor_usage (
 id UUID PRIMARY KEY,
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 user_id UUID REFERENCES identity_user(id) ON DELETE SET NULL,
 created_at TIMESTAMPTZ NOT NULL,
 finished_at TIMESTAMPTZ,
 -- PENDING while the question runs; it still counts against the quota so concurrent questions
 -- cannot overshoot it.
 outcome VARCHAR(20) NOT NULL CHECK (outcome IN
  ('PENDING','ANSWERED','REFUSED','UNGROUNDED','PROVIDER_ERROR','CANCELLED')),
 model VARCHAR(60),
 input_tokens INTEGER NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
 cache_read_tokens INTEGER NOT NULL DEFAULT 0 CHECK (cache_read_tokens >= 0),
 output_tokens INTEGER NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
 tool_calls SMALLINT NOT NULL DEFAULT 0 CHECK (tool_calls >= 0),
 tools_used VARCHAR(200),
 estimated_cost_usd NUMERIC(12,6) NOT NULL DEFAULT 0 CHECK (estimated_cost_usd >= 0),
 CHECK ((outcome = 'PENDING') = (finished_at IS NULL))
);
CREATE INDEX advisor_usage_tenant_month_idx ON advisor_usage (tenant_id, created_at);
CREATE INDEX advisor_usage_created_idx ON advisor_usage (created_at);
