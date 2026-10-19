-- SC-011 (ADR-0012, ajustes da revisão do PR #8): validate the reservation -> order foreign key
-- created NOT VALID in V13. Every reservation written since V13 already satisfies it. A database
-- still holding reservations from before orders existed (old local fixtures) keeps the constraint
-- NOT VALID and gets a warning instead of a failed startup; those rows are never deleted here.
-- VALIDATE takes a SHARE UPDATE EXCLUSIVE lock, so reads and writes continue meanwhile.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM inventory_reservation r
    WHERE NOT EXISTS (
      SELECT 1 FROM sales_order o WHERE o.id = r.order_id AND o.tenant_id = r.tenant_id))
  THEN
    RAISE WARNING 'inventory_reservation has rows without an order of the same tenant; '
      'inventory_reservation_order_tenant_fk stays NOT VALID until they are reviewed';
  ELSE
    ALTER TABLE inventory_reservation VALIDATE CONSTRAINT inventory_reservation_order_tenant_fk;
  END IF;
END $$;
