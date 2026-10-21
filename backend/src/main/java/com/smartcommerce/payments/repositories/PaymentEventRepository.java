package com.smartcommerce.payments.repositories;

import com.smartcommerce.payments.domain.PaymentEventRecord;
import com.smartcommerce.payments.domain.PaymentNotification;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Provider notifications (SC-014). The queue is cross-tenant by nature; each row carries the tenant
 * resolved from the stored payment, and processing uses only that tenant.
 */
@Repository
public class PaymentEventRepository {
  private final JdbcTemplate jdbc;

  public PaymentEventRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Records a notification once per event key. {@code tenant} null means the provider order is
   * unknown here: the row is stored already IGNORED, for the audit trail only.
   */
  public boolean insert(PaymentNotification event, UUID tenant, Instant now) {
    return jdbc.update(
            """
            INSERT INTO payment_event(id,provider,event_key,topic,action,provider_order_id,
              provider_user_id,tenant_id,status,next_attempt_at,received_at,processed_at)
            VALUES (?,'MERCADO_PAGO',?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (provider,event_key) DO NOTHING
            """,
            UUID.randomUUID(),
            event.eventKey(),
            event.topic(),
            event.action(),
            event.providerOrderId(),
            event.providerUserId(),
            tenant,
            tenant == null ? "IGNORED" : "PENDING",
            utc(now),
            utc(now),
            tenant == null ? utc(now) : null)
        == 1;
  }

  /**
   * Claims up to {@code limit} due events: each claim counts an attempt and pushes the next one to
   * {@code leaseUntil}, so a crashed worker's events come back later. SKIP LOCKED lets several
   * instances split the queue.
   */
  public List<PaymentEventRecord> claimDue(Instant now, Instant leaseUntil, int limit) {
    return jdbc.query(
        """
        UPDATE payment_event SET attempts=attempts+1, next_attempt_at=?
        WHERE id IN (SELECT id FROM payment_event WHERE status='PENDING' AND next_attempt_at<=?
          ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED)
        RETURNING id,tenant_id,provider_order_id,attempts
        """,
        (row, index) ->
            new PaymentEventRecord(
                row.getObject(1, UUID.class),
                row.getObject(2, UUID.class),
                row.getString(3),
                row.getInt(4)),
        utc(leaseUntil),
        utc(now),
        limit);
  }

  /** Closes an event as PROCESSED, IGNORED or FAILED. */
  public void finish(UUID event, String status, String error, Instant now) {
    jdbc.update(
        """
        UPDATE payment_event SET status=?, last_error=?, processed_at=?
        WHERE id=? AND status='PENDING'
        """,
        status,
        error,
        utc(now),
        event);
  }

  /** Keeps an event PENDING for another attempt at {@code next}. */
  public void retry(UUID event, Instant next, String error) {
    jdbc.update(
        "UPDATE payment_event SET next_attempt_at=?, last_error=? WHERE id=? AND status='PENDING'",
        utc(next),
        error,
        event);
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
