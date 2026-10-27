-- SC-031 (ADR-0017 B1, B2, B7): one billing account per owner. The 14-day trial lives here, without a
-- card or a Stripe object; Stripe identifiers arrive with the subscription (SC-032). Additive only.
CREATE TABLE billing_account (
    user_id UUID PRIMARY KEY REFERENCES identity_user(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    plan VARCHAR(16),
    billing_cycle VARCHAR(16),
    trial_ends_at TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ,
    past_due_since TIMESTAMPTZ,
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT billing_account_status_valid CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELED')),
    CONSTRAINT billing_account_plan_valid CHECK (plan IS NULL OR plan IN ('ESSENCIAL', 'PROFISSIONAL', 'ESCALA')),
    CONSTRAINT billing_account_cycle_valid CHECK (billing_cycle IS NULL OR billing_cycle IN ('MONTHLY', 'SEMIANNUAL', 'ANNUAL')),
    -- A paid state always names its plan and cycle; the trial never does.
    CONSTRAINT billing_account_plan_state CHECK (
        (status = 'TRIAL' AND plan IS NULL AND billing_cycle IS NULL)
        OR (status <> 'TRIAL' AND plan IS NOT NULL AND billing_cycle IS NOT NULL)
    ),
    CONSTRAINT billing_account_past_due_state CHECK (status <> 'PAST_DUE' OR past_due_since IS NOT NULL),
    CONSTRAINT billing_account_version_valid CHECK (version >= 0),
    CONSTRAINT billing_account_timestamps_valid CHECK (updated_at >= created_at),
    CONSTRAINT billing_account_customer_unique UNIQUE (stripe_customer_id),
    CONSTRAINT billing_account_subscription_unique UNIQUE (stripe_subscription_id)
);

COMMENT ON TABLE billing_account IS 'Platform subscription of an owner (ADR-0017); limits apply to every company the owner holds';
