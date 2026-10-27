-- SC-032 (ADR-0017 B6): Stripe webhook events already processed, so a redelivery is acknowledged
-- without calling Stripe again. The event body is not kept: the account is reconciled from the API.
CREATE TABLE billing_event (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(128) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX billing_event_received_at ON billing_event (received_at);

COMMENT ON TABLE billing_event IS 'Processed Stripe webhook event ids (ADR-0017 B6); no payload or personal data';
