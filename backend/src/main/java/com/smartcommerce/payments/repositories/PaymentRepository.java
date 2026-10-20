package com.smartcommerce.payments.repositories;

import com.smartcommerce.payments.domain.PaymentRecord;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Payments of sales orders (SC-013); every statement is scoped by the tenant it is given. */
@Repository
public class PaymentRepository {
  private final JdbcTemplate jdbc;

  public PaymentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Whether the store has a connected provider account (tokens present). */
  public boolean connected(UUID tenant) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM payment_account WHERE tenant_id=? AND status='CONNECTED')",
            Boolean.class,
            tenant));
  }

  /**
   * Returns the order's payment, inserting it as CREATING first if there is none. Concurrent calls
   * converge on the single row allowed by {@code payment_order_unique}.
   */
  public PaymentRecord claim(UUID tenant, UUID order, BigDecimal amount, Instant now) {
    jdbc.update(
        """
        INSERT INTO payment(id,tenant_id,order_id,provider,status,currency,amount,created_at,updated_at)
        VALUES (?,?,?,'MERCADO_PAGO','CREATING','BRL',?,?,?)
        ON CONFLICT (order_id) DO NOTHING
        """,
        UUID.randomUUID(),
        tenant,
        order,
        amount,
        utc(now),
        utc(now));
    return find(tenant, order).orElseThrow(() -> new IllegalStateException("Payment not stored"));
  }

  public Optional<PaymentRecord> find(UUID tenant, UUID order) {
    return jdbc
        .query(
            """
            SELECT id,status,amount,provider_order_id,checkout_url FROM payment
            WHERE tenant_id=? AND order_id=?
            """,
            PaymentRepository::payment,
            tenant,
            order)
        .stream()
        .findFirst();
  }

  /** Records the provider order; only a CREATING row changes, so the first answer wins. */
  public boolean markCreated(
      UUID tenant,
      UUID payment,
      String providerUserId,
      String providerOrderId,
      String providerStatus,
      String checkoutUrl,
      Instant expiresAt,
      Instant now) {
    return jdbc.update(
            """
            UPDATE payment SET status='CREATED', provider_user_id=?, provider_order_id=?,
              provider_status=?, checkout_url=?, provider_expires_at=?, updated_at=?,
              version=version+1
            WHERE tenant_id=? AND id=? AND status='CREATING'
            """,
            providerUserId,
            providerOrderId,
            providerStatus,
            checkoutUrl,
            utc(expiresAt),
            utc(now),
            tenant,
            payment)
        == 1;
  }

  private static PaymentRecord payment(ResultSet row, int index) throws SQLException {
    return new PaymentRecord(
        row.getObject("id", UUID.class),
        row.getString("status"),
        row.getBigDecimal("amount"),
        row.getString("provider_order_id"),
        row.getString("checkout_url"));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
