package com.smartcommerce.orders;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Minimal valid PENDING_PAYMENT order for tests that need an order id: since V13 every reservation
 * references an order of the same tenant. Synthetic data only.
 */
public final class TestOrders {
  private TestOrders() {}

  public static UUID create(JdbcTemplate jdbc, UUID tenant) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO sales_order(id,tenant_id,store_id,number,status,currency,subtotal_amount,
          shipping_amount,discount_amount,total_amount,fulfillment_type,fulfillment_text,
          buyer_name,buyer_email,buyer_email_key,buyer_phone,reservation_expires_at,request_id,
          request_fingerprint,access_token_hash,created_at,updated_at)
        SELECT ?,s.tenant_id,s.id,
          ?,
          'PENDING_PAYMENT','BRL',1.00,0,0,1.00,'PICKUP','Retirada sintética',
          'Comprador sintético','buyer@example.test','buyer@example.test','11999990000',
          now()+interval '30 minutes',?,repeat('0',64),repeat('0',64),now(),now()
        FROM store s WHERE s.tenant_id=?
        """,
        id,
        // Random number: tests create orders concurrently; collisions are practically impossible.
        java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
        UUID.randomUUID(),
        tenant);
    return id;
  }
}
