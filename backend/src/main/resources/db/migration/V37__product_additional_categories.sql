-- Sprint 8, SC-041 pending item done with SC-043 (ADR-0018 V1): a product may also appear in other
-- categories besides its primary one ("Camisetas" and "Promoções"). Additive. The primary category
-- stays in catalog_product.category_id (breadcrumb, related products); these are the extra ones.
-- Both composite keys keep a product and a category of the same tenant; removing either side
-- removes the link.
CREATE TABLE catalog_product_category (
    product_id UUID NOT NULL,
    category_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT catalog_product_category_product_fk FOREIGN KEY (product_id, tenant_id)
        REFERENCES catalog_product(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT catalog_product_category_category_fk FOREIGN KEY (category_id, tenant_id)
        REFERENCES catalog_category(id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX catalog_product_category_category_idx
    ON catalog_product_category(tenant_id, category_id);
