package com.smartcommerce.payments.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Full refunds of payments (SC-016); every statement is scoped by the tenant it is given. */
@Repository
public class PaymentRefundRepository {
  private final JdbcTemplate jdbc;

  public PaymentRefundRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Records the store's refund once per payment; a repeat leaves the first one unchanged. */
  public void request(
      UUID tenant,
      UUID payment,
      UUID order,
      BigDecimal amount,
      String reason,
      boolean restock,
      UUID actor,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO payment_refund(id,tenant_id,payment_id,order_id,status,amount,reason,restock,
          requested_by,requested_at)
        VALUES (?,?,?,?,'REQUESTED',?,?,?,?,?)
        ON CONFLICT (payment_id) DO NOTHING
        """,
        UUID.randomUUID(),
        tenant,
        payment,
        order,
        amount,
        reason,
        restock,
        actor,
        utc(now));
  }

  public Optional<Refund> find(UUID tenant, UUID payment) {
    return jdbc
        .query(
            "SELECT status,restock FROM payment_refund WHERE tenant_id=? AND payment_id=?",
            (row, index) -> new Refund(row.getString(1), row.getBoolean(2)),
            tenant,
            payment)
        .stream()
        .findFirst();
  }

  /** The provider took the request (or said it was already refunding). */
  public void accepted(UUID tenant, UUID payment, Instant now) {
    jdbc.update(
        """
        UPDATE payment_refund SET provider_accepted_at=COALESCE(provider_accepted_at,?),
          last_error=NULL
        WHERE tenant_id=? AND payment_id=? AND status='REQUESTED'
        """,
        utc(now),
        tenant,
        payment);
  }

  public void failed(UUID tenant, UUID payment, String error) {
    jdbc.update(
        """
        UPDATE payment_refund SET last_error=?
        WHERE tenant_id=? AND payment_id=? AND status='REQUESTED'
        """,
        error,
        tenant,
        payment);
  }

  /** REQUESTED to CONFIRMED once; the caller holds the payment row lock. */
  public boolean confirm(UUID tenant, UUID payment, Instant now) {
    return jdbc.update(
            """
            UPDATE payment_refund SET status='CONFIRMED', confirmed_at=?, last_error=NULL
            WHERE tenant_id=? AND payment_id=? AND status='REQUESTED'
            """,
            utc(now),
            tenant,
            payment)
        == 1;
  }

  public record Refund(String status, boolean restock) {
    public boolean pending() {
      return "REQUESTED".equals(status);
    }
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
