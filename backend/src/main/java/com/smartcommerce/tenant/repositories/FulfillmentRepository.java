package com.smartcommerce.tenant.repositories;

import com.smartcommerce.tenant.domain.FulfillmentSettings;
import com.smartcommerce.tenant.domain.StoreFulfillment;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException;
import com.smartcommerce.tenant.exceptions.TenantPersistenceException.Reason;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Optimistic writes are single conditional statements: the first save inserts version 1 only when
 * no row exists, later saves update only the expected version.
 */
@Repository
public class FulfillmentRepository {
  private final JdbcTemplate jdbc;
  private final TenantAccessRepository access;

  public FulfillmentRepository(JdbcTemplate jdbc, TenantAccessRepository access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  public Optional<StoreFulfillment> findOwned(UUID actor, UUID tenant) {
    return jdbc
        .query(
            """
 SELECT f.pickup_enabled,f.pickup_instructions,f.fixed_shipping_enabled,f.fixed_shipping_amount,
        f.fixed_shipping_area,COALESCE(f.version,0) AS version
 FROM store s
 JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=? AND m.role='OWNER'
 JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
 LEFT JOIN store_fulfillment f ON f.store_id=s.id AND f.tenant_id=s.tenant_id
 WHERE s.tenant_id=?
 """,
            (row, index) ->
                new StoreFulfillment(
                    new FulfillmentSettings(
                        row.getBoolean("pickup_enabled"),
                        row.getString("pickup_instructions"),
                        row.getBoolean("fixed_shipping_enabled"),
                        row.getObject("fixed_shipping_amount", BigDecimal.class),
                        row.getString("fixed_shipping_area")),
                    row.getLong("version")),
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  /** Returns false on a stale version; authorization locks last until the transaction ends. */
  public boolean save(
      UUID actor, UUID tenant, FulfillmentSettings settings, long version, Instant now) {
    UUID store =
        access
            .lockOwnedStore(actor, tenant)
            .orElseThrow(() -> new TenantPersistenceException(Reason.MISSING));
    Timestamp updatedAt = Timestamp.from(now);
    if (version == 0) {
      return jdbc.update(
              """
   INSERT INTO store_fulfillment(store_id,tenant_id,pickup_enabled,pickup_instructions,
     fixed_shipping_enabled,fixed_shipping_amount,fixed_shipping_area,version,updated_at)
   VALUES(?,?,?,?,?,?,?,1,?) ON CONFLICT(store_id) DO NOTHING
   """,
              store,
              tenant,
              settings.pickupEnabled(),
              settings.pickupInstructions(),
              settings.fixedShippingEnabled(),
              settings.fixedShippingAmount(),
              settings.fixedShippingArea(),
              updatedAt)
          == 1;
    }
    return jdbc.update(
            """
   UPDATE store_fulfillment SET pickup_enabled=?,pickup_instructions=?,fixed_shipping_enabled=?,
     fixed_shipping_amount=?,fixed_shipping_area=?,version=version+1,updated_at=?
   WHERE store_id=? AND tenant_id=? AND version=?
   """,
            settings.pickupEnabled(),
            settings.pickupInstructions(),
            settings.fixedShippingEnabled(),
            settings.fixedShippingAmount(),
            settings.fixedShippingArea(),
            updatedAt,
            store,
            tenant,
            version)
        == 1;
  }
}
