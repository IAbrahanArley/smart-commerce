-- Sprint 8, SC-042 (ADR-0018 V4): the most card installments the store offers in Checkout Pro.
-- Additive. NULL keeps today's behavior: no limit sent, Mercado Pago's own default applies, and
-- the storefront promises no installments. Interest depends on the seller's Mercado Pago account.
ALTER TABLE store ADD COLUMN max_installments SMALLINT
    CHECK (max_installments IS NULL OR max_installments BETWEEN 1 AND 12);
