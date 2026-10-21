package com.smartcommerce.payments.repositories;

import com.smartcommerce.payments.domain.PaymentRecord;
import com.smartcommerce.payments.domain.PaymentSummary;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Payments of sales orders (SC-013); every statement is scoped by the tenant it is given. */
@Repository
public class PaymentRepository {
  /** SC-015: the reconciliation routine first looks at a new provider order after this delay. */
  public static final Duration FIRST_CHECK = Duration.ofMinutes(1);

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
            SELECT + " WHERE tenant_id=? AND order_id=?", PaymentRepository::payment, tenant, order)
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
              next_check_at=?, version=version+1
            WHERE tenant_id=? AND id=? AND status='CREATING'
            """,
            providerUserId,
            providerOrderId,
            providerStatus,
            checkoutUrl,
            utc(expiresAt),
            utc(now),
            utc(now.plus(FIRST_CHECK)),
            tenant,
            payment)
        == 1;
  }

  /**
   * Claims up to {@code limit} CREATED payments due for a provider query whose provider order
   * expired less than {@code horizon} ago (SC-015). The claim pushes {@code next_check_at} to
   * {@code leaseUntil}, so a crashed run's rows come back later; SKIP LOCKED splits the work
   * between instances. Each row keeps its own tenant.
   */
  public List<DueCheck> claimDueChecks(
      Instant now, Instant horizon, Instant extendedHorizon, Instant leaseUntil, int limit) {
    return jdbc.query(
        """
        UPDATE payment SET next_check_at=?, check_attempts=check_attempts+1
        WHERE id IN (SELECT p.id FROM payment p
          WHERE p.next_check_at<=? AND (
            (p.status='CREATED' AND (p.provider_expires_at>?
              OR (p.provider_expires_at>? AND p.last_check_error IN
                ('NOT_CONNECTED','REVOKED','ACCOUNT_CHANGED'))))
            OR (p.status='APPROVED' AND EXISTS(SELECT 1 FROM payment_refund r
              WHERE r.payment_id=p.id AND r.status='REQUESTED' AND r.requested_at>?)))
          ORDER BY p.next_check_at LIMIT ? FOR UPDATE OF p SKIP LOCKED)
        RETURNING id,tenant_id,provider_order_id,provider_expires_at,status
        """,
        (row, index) ->
            new DueCheck(
                row.getObject(1, UUID.class),
                row.getObject(2, UUID.class),
                row.getString(3),
                row.getObject(4, OffsetDateTime.class).toInstant(),
                "APPROVED".equals(row.getString(5))),
        utc(leaseUntil),
        utc(now),
        utc(horizon),
        utc(extendedHorizon),
        utc(extendedHorizon),
        limit);
  }

  /**
   * Schedules the next query of a payment still waiting (CREATED, or APPROVED with a refund to
   * confirm) and records why the last one did not settle it.
   */
  public void scheduleCheck(UUID tenant, UUID payment, Instant next, String error) {
    jdbc.update(
        """
        UPDATE payment SET next_check_at=?, last_check_error=?
        WHERE tenant_id=? AND id=? AND status IN ('CREATED','APPROVED')
        """,
        utc(next),
        error,
        tenant,
        payment);
  }

  /** What the store sees about the payment of one of its orders (SC-015). */
  public Optional<PaymentSummary> summary(UUID tenant, UUID order) {
    return jdbc
        .query(
            """
            SELECT p.provider_order_id,p.status,p.provider_status,p.provider_status_detail,
              p.amount,p.paid_amount,p.approved_at,p.checked_at,p.last_check_error,
              r.status,r.reason,r.restock,r.requested_at,r.provider_accepted_at,r.confirmed_at,
              r.last_error
            FROM payment p LEFT JOIN payment_refund r ON r.payment_id=p.id AND r.tenant_id=p.tenant_id
            WHERE p.tenant_id=? AND p.order_id=?
            """,
            (row, index) ->
                new PaymentSummary(
                    row.getString(1),
                    row.getString(2),
                    row.getString(3),
                    row.getString(4),
                    row.getBigDecimal(5),
                    row.getBigDecimal(6),
                    instant(row, 7),
                    instant(row, 8),
                    row.getString(9),
                    row.getString(10) == null
                        ? null
                        : new PaymentSummary.Refund(
                            row.getString(10),
                            row.getString(11),
                            row.getBoolean(12),
                            instant(row, 13),
                            instant(row, 14),
                            instant(row, 15),
                            row.getString(16))),
            tenant,
            order)
        .stream()
        .findFirst();
  }

  public record DueCheck(
      UUID id, UUID tenant, String providerOrderId, Instant providerExpiresAt, boolean refund) {}

  /** Locks the payment of an order inside its tenant (SC-016). */
  public Optional<PaymentRecord> lockByOrder(UUID tenant, UUID order) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND order_id=? FOR UPDATE",
            PaymentRepository::payment,
            tenant,
            order)
        .stream()
        .findFirst();
  }

  /** APPROVED to REFUNDED once; the caller holds the row lock. */
  public boolean markRefunded(
      UUID tenant, UUID payment, String status, String detail, Instant now) {
    return jdbc.update(
            """
            UPDATE payment SET status='REFUNDED', provider_status=?, provider_status_detail=?,
              checked_at=?, updated_at=?, last_check_error=NULL, version=version+1
            WHERE tenant_id=? AND id=? AND status='APPROVED'
            """,
            status,
            detail,
            utc(now),
            utc(now),
            tenant,
            payment)
        == 1;
  }

  /** Asks the reconciliation to look at this payment from {@code next} on. */
  public void checkFrom(UUID tenant, UUID payment, Instant next) {
    jdbc.update(
        "UPDATE payment SET next_check_at=? WHERE tenant_id=? AND id=?",
        utc(next),
        tenant,
        payment);
  }

  private static Instant instant(ResultSet row, int column) throws SQLException {
    OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /**
   * The tenant owning a provider order (SC-014). The only cross-tenant read: it routes a
   * notification to the store that created the order, never the notification's own claims.
   */
  public Optional<ProviderOrderOwner> ownerOfProviderOrder(String providerOrderId) {
    // Case-insensitive: the signature admits a lowercased id; the stored spelling is returned.
    List<ProviderOrderOwner> found =
        jdbc.query(
            """
            SELECT tenant_id,provider_order_id FROM payment
            WHERE provider='MERCADO_PAGO' AND lower(provider_order_id)=lower(?) LIMIT 2
            """,
            (row, index) -> new ProviderOrderOwner(row.getObject(1, UUID.class), row.getString(2)),
            providerOrderId);
    // Ids differing only by case in two stores would be ambiguous: route neither (SC-017 review).
    return found.size() == 1 ? Optional.of(found.getFirst()) : Optional.empty();
  }

  public record ProviderOrderOwner(UUID tenant, String providerOrderId) {}

  /** The payment of a provider order inside its tenant, without locking. */
  public Optional<PaymentRecord> findByProviderOrder(UUID tenant, String providerOrderId) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND provider_order_id=?",
            PaymentRepository::payment,
            tenant,
            providerOrderId)
        .stream()
        .findFirst();
  }

  /** Locks the payment of a provider order inside its tenant; serializes confirmations. */
  public Optional<PaymentRecord> lockByProviderOrder(UUID tenant, String providerOrderId) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND provider_order_id=? FOR UPDATE",
            PaymentRepository::payment,
            tenant,
            providerOrderId)
        .stream()
        .findFirst();
  }

  /** Records what the provider reported, without changing the payment state. */
  public void recordCheck(
      UUID tenant,
      UUID payment,
      String providerStatus,
      String detail,
      BigDecimal paidAmount,
      Instant now) {
    jdbc.update(
        """
        UPDATE payment SET provider_status=?, provider_status_detail=?, paid_amount=?,
          checked_at=?, updated_at=?, version=version+1
        WHERE tenant_id=? AND id=? AND status='CREATED'
        """,
        providerStatus,
        detail,
        paidAmount,
        utc(now),
        utc(now),
        tenant,
        payment);
  }

  /** CREATED to APPROVED once; the caller holds the row lock. */
  public boolean approve(
      UUID tenant,
      UUID payment,
      String providerStatus,
      String detail,
      BigDecimal paidAmount,
      Instant now) {
    return jdbc.update(
            """
            UPDATE payment SET status='APPROVED', provider_status=?, provider_status_detail=?,
              paid_amount=?, approved_at=?, checked_at=?, updated_at=?, version=version+1
            WHERE tenant_id=? AND id=? AND status='CREATED'
            """,
            providerStatus,
            detail,
            paidAmount,
            utc(now),
            utc(now),
            utc(now),
            tenant,
            payment)
        == 1;
  }

  private static final String SELECT =
      """
      SELECT id,order_id,status,amount,provider_order_id,provider_user_id,checkout_url
      FROM payment""";

  private static PaymentRecord payment(ResultSet row, int index) throws SQLException {
    return new PaymentRecord(
        row.getObject("id", UUID.class),
        row.getObject("order_id", UUID.class),
        row.getString("status"),
        row.getBigDecimal("amount"),
        row.getString("provider_order_id"),
        row.getString("provider_user_id"),
        row.getString("checkout_url"));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
