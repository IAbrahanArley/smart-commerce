-- Technical bootstrap marker, independent of tenant and business schemas.
-- Proves that versioned migrations run against PostgreSQL without anticipating SC-003.
CREATE TABLE app_metadata (
    metadata_key VARCHAR(64) PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL
);

COMMENT ON TABLE app_metadata IS 'Technical application metadata only; no domain or tenant data';

INSERT INTO app_metadata (metadata_key, metadata_value) VALUES ('bootstrap', 'SC-001');
