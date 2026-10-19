package com.smartcommerce.orders.repositories;

import com.smartcommerce.orders.domain.AdminOrderRecords;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cross-tenant batches of the scheduled routines (SC-010). Each row keeps its own tenant; SKIP
 * LOCKED lets concurrent instances split the work, and every write is a compare-and-set on the
 * state, so a row is never processed twice.
 */
@Repository
public class OrderMaintenanceRepository {
  private final JdbcTemplate jdbc;

  public OrderMaintenanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks up to {@code limit} pending orders past their deadline (skipping rows locked now). */
  public List<AdminOrderRecords.Due> lockDue(Instant now, int limit) {
    return jdbc.query(
        """
        SELECT id,tenant_id FROM sales_order
        WHERE status='PENDING_PAYMENT' AND reservation_expires_at<=?
        ORDER BY reservation_expires_at LIMIT ? FOR UPDATE SKIP LOCKED
        """,
        (row, index) ->
            new AdminOrderRecords.Due(row.getObject(1, UUID.class), row.getObject(2, UUID.class)),
        utc(now),
        limit);
  }

  public boolean expire(UUID tenant, UUID order, Instant now) {
    return jdbc.update(
            """
            UPDATE sales_order SET status='EXPIRED',expired_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND status='PENDING_PAYMENT' AND reservation_expires_at<=?
            """,
            utc(now),
            utc(now),
            tenant,
            order,
            utc(now))
        == 1;
  }

  /**
   * D2: removes the buyer's personal data from closed orders created before {@code cutoff}. The
   * request fingerprint is derived from that data, so it is replaced too; amounts, items and dates
   * stay. Returns the number of anonymized orders.
   */
  public int anonymize(Instant cutoff, Instant now, int limit) {
    return jdbc.update(
        """
        UPDATE sales_order SET buyer_name=NULL,buyer_email=NULL,buyer_email_key=NULL,buyer_phone=NULL,
          address_line1=NULL,address_line2=NULL,address_district=NULL,address_city=NULL,
          address_state=NULL,address_postal_code=NULL,request_fingerprint=repeat('0',64),
          anonymized_at=?,updated_at=?,version=version+1
        WHERE id IN (SELECT id FROM sales_order
            WHERE anonymized_at IS NULL AND status IN ('EXPIRED','CANCELLED') AND created_at<=?
            ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED)
          AND anonymized_at IS NULL AND status IN ('EXPIRED','CANCELLED')
        """,
        utc(now),
        utc(now),
        utc(cutoff),
        limit);
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
