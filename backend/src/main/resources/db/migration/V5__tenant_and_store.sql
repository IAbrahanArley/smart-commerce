-- SC-003: identity remains global; every store and membership belongs to a tenant.
-- Creation of tenant + OWNER membership + single draft store is one service transaction.
CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    company_name VARCHAR(120) NOT NULL CHECK (char_length(btrim(company_name)) BETWEEN 1 AND 120),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tenant_membership (
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES identity_user(id) ON DELETE RESTRICT,
    role VARCHAR(16) NOT NULL CHECK (role = 'OWNER'),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, user_id)
);
CREATE INDEX tenant_membership_user_idx ON tenant_membership(user_id, tenant_id);

CREATE TABLE store (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenant(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 120),
    slug VARCHAR(63) COLLATE "C" NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status = 'DRAFT'),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (char_length(slug) BETWEEN 3 AND 63 AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
