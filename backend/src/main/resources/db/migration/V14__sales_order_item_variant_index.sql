-- Additive (review of PR #8): backs the RESTRICT foreign key sales_order_item(variant_id, tenant_id)
-- so deleting or rechecking a variant does not scan every order item. The order-side foreign keys
-- are already covered by the UNIQUE (order_id, ...) constraints of sales_order_item and
-- inventory_reservation.
CREATE INDEX sales_order_item_variant_idx ON sales_order_item(variant_id, tenant_id);
