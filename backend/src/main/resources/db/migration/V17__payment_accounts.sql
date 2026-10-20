-- SC-012 (ADR-0013 P3, P10): the store OWNER connects a Mercado Pago account through OAuth with
-- PKCE. Additive: two new tables, no existing data changes.

-- One pending authorization per attempt. Only the SHA-256 of the state is stored, so the table
-- never holds a usable state; the PKCE verifier is encrypted (AES-256-GCM). Single use: used_at.
CREATE TABLE payment_oauth_state (
 state_hash BYTEA PRIMARY KEY CHECK (octet_length(state_hash) = 32),
 tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES identity_user(id) ON DELETE CASCADE,
 code_verifier BYTEA NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 used_at TIMESTAMPTZ,
 CHECK (expires_at > created_at)
);
CREATE INDEX payment_oauth_state_expires_idx ON payment_oauth_state (expires_at);

-- At most one provider account per store. Tokens are AES-256-GCM ciphertexts bound to the tenant
-- (associated data); they exist exactly while the account is CONNECTED. Disconnection and
-- revocation erase them and keep only the non-secret account id for the owner's history.
CREATE TABLE payment_account (
 tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
 provider VARCHAR(20) NOT NULL CHECK (provider = 'MERCADO_PAGO'),
 provider_user_id VARCHAR(40) NOT NULL CHECK (provider_user_id ~ '^[0-9]{1,40}$'),
 status VARCHAR(20) NOT NULL CHECK (status IN ('CONNECTED','REVOKED','DISCONNECTED')),
 access_token BYTEA,
 refresh_token BYTEA,
 access_expires_at TIMESTAMPTZ,
 key_version SMALLINT,
 live_mode BOOLEAN,
 connected_by UUID REFERENCES identity_user(id) ON DELETE SET NULL,
 connected_at TIMESTAMPTZ NOT NULL,
 refreshed_at TIMESTAMPTZ,
 closed_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK (
  (status = 'CONNECTED'
    AND access_token IS NOT NULL AND refresh_token IS NOT NULL
    AND access_expires_at IS NOT NULL AND key_version IS NOT NULL AND closed_at IS NULL)
  OR (status IN ('REVOKED','DISCONNECTED')
    AND access_token IS NULL AND refresh_token IS NULL AND closed_at IS NOT NULL))
);
CREATE INDEX payment_account_refresh_idx ON payment_account (access_expires_at)
 WHERE status = 'CONNECTED';
