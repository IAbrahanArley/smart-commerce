-- SC-049 (ADR-0019 C1): store employees (STAFF) invited by the OWNER. Additive only.
-- Every existing access query already requires role='OWNER', so STAFF starts with no access
-- to any existing endpoint; the features STAFF may use come with their own checks.
ALTER TABLE tenant_membership DROP CONSTRAINT tenant_membership_role_check;
ALTER TABLE tenant_membership
    ADD CONSTRAINT tenant_membership_role_check CHECK (role IN ('OWNER', 'STAFF'));
-- Exactly one OWNER per company (always true so far, now enforced).
CREATE UNIQUE INDEX tenant_membership_one_owner ON tenant_membership(tenant_id) WHERE role = 'OWNER';

-- One row per invitation and its whole lifecycle: sent, accepted (or canceled), access revoked.
-- Only the digest of the single-use token is stored; the e-mail is the minimum to match the
-- invited account (comparison key) and to show it to the OWNER (original spelling).
CREATE TABLE staff_invitation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    email_key VARCHAR(320) NOT NULL,
    email_original VARCHAR(320) NOT NULL,
    token_digest CHAR(64) NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
    canceled_at TIMESTAMPTZ,
    access_revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES identity_user(id) ON DELETE RESTRICT,
    CHECK (expires_at > created_at),
    CHECK ((accepted_at IS NULL) = (accepted_by IS NULL)),
    CHECK (NOT (accepted_at IS NOT NULL AND canceled_at IS NOT NULL)),
    CHECK (access_revoked_at IS NULL OR accepted_at IS NOT NULL)
);
-- At most one open invitation per e-mail and company; a new one cancels the previous.
CREATE UNIQUE INDEX staff_invitation_open
    ON staff_invitation(tenant_id, email_key) WHERE accepted_at IS NULL AND canceled_at IS NULL;
CREATE INDEX staff_invitation_tenant ON staff_invitation(tenant_id, created_at);
