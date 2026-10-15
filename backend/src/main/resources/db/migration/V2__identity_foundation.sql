-- SC-002 stage 1: global identity and credentials only. No activation, session or tenant schema.
CREATE TABLE identity_user (
    id UUID PRIMARY KEY,
    email_original VARCHAR(1024) NOT NULL,
    email_key VARCHAR(254) COLLATE "C" NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING_EMAIL',
    auth_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    CONSTRAINT identity_user_email_unique UNIQUE (email_key),
    CONSTRAINT identity_user_status_valid CHECK (status IN ('PENDING_EMAIL', 'ACTIVE', 'DISABLED')),
    CONSTRAINT identity_user_auth_version_valid CHECK (auth_version >= 0),
    CONSTRAINT identity_user_verified_state CHECK (
        (status <> 'ACTIVE' OR verified_at IS NOT NULL)
        AND (status <> 'PENDING_EMAIL' OR verified_at IS NULL)
    ),
    CONSTRAINT identity_user_timestamps_valid CHECK (
        updated_at >= created_at AND (verified_at IS NULL OR verified_at >= created_at)
    )
);

CREATE TABLE identity_local_credential (
    user_id UUID PRIMARY KEY REFERENCES identity_user(id) ON DELETE RESTRICT,
    password_hash VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT identity_credential_hash_format CHECK (
        password_hash ~ '^\{pbkdf2-sha256-nfc-i[1-9][0-9]{5,6}-s16-l256-v1\}[0-9a-f]{96}$'
    ),
    CONSTRAINT identity_credential_hash_cost CHECK (
        substring(password_hash FROM 'nfc-i([1-9][0-9]{5,6})-s16')::INTEGER BETWEEN 600000 AND 2000000
    ),
    CONSTRAINT identity_credential_timestamps_valid CHECK (updated_at >= created_at)
);

COMMENT ON TABLE identity_user IS 'Global identity only; no tenant or role authorization';
COMMENT ON COLUMN identity_user.email_key IS 'ASCII local-part preserved; JDK IDNA STD3 lowercase domain; C collation';
COMMENT ON TABLE identity_local_credential IS 'Versioned password hashes only; pending identities cannot receive credentials through the repository';
-- Cross-table eligibility is enforced by locked repository writes. Direct SQL remains privileged;
-- future activation must prove possession and atomically create the credential with the state change.
