-- MVP delivery, block 1a (RELEASE_PLAN.md): password recovery for active accounts.
-- Additive. Same shape as identity_email_verification: raw tokens never enter the database, only
-- their SHA-256 digest; a token is single use and short lived. Kept apart from email verification
-- because it applies to ACTIVE accounts and replaces an existing credential.
CREATE TABLE identity_password_reset (
    token_digest CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_user(id) ON DELETE CASCADE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT identity_password_reset_digest CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT identity_password_reset_dates CHECK (expires_at > issued_at AND (consumed_at IS NULL OR consumed_at >= issued_at))
);
CREATE INDEX identity_password_reset_user_idx ON identity_password_reset(user_id);
CREATE INDEX identity_password_reset_expiry_idx ON identity_password_reset(expires_at);
