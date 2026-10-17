-- Email ownership proof only. Raw tokens never enter the database.
CREATE TABLE identity_email_verification (
    token_digest CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_user(id) ON DELETE CASCADE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT identity_verification_digest CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT identity_verification_dates CHECK (expires_at > issued_at AND (consumed_at IS NULL OR consumed_at >= issued_at))
);
CREATE INDEX identity_verification_user_idx ON identity_email_verification(user_id);
CREATE INDEX identity_verification_expiry_idx ON identity_email_verification(expires_at);

-- Keys are SHA-256 pseudonyms, never addresses, IPs or token values.
CREATE TABLE identity_rate_limit (
    bucket_key VARCHAR(100) PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts >= 0),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX identity_rate_limit_expiry_idx ON identity_rate_limit(expires_at);
