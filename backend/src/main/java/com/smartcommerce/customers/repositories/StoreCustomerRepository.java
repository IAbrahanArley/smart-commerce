package com.smartcommerce.customers.repositories;

import com.smartcommerce.customers.domain.CustomerInput;
import com.smartcommerce.customers.domain.StoreCustomer;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The store's customers (SC-055); every statement is scoped by the tenant it is given, after {@link
 * #lockMember} authorized the actor.
 */
@Repository
public class StoreCustomerRepository {
  private final JdbcTemplate jdbc;

  public StoreCustomerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The actor's role in the store (OWNER or STAFF) with an active, verified identity, locking the
   * store while the transaction lasts ({@code forUpdate} serializes the store's customer and credit
   * writes); empty for anyone else.
   */
  public Optional<String> lockMember(UUID actor, UUID tenant, boolean forUpdate) {
    return jdbc
        .queryForList(
            """
            SELECT m.role FROM store s
            JOIN tenant_membership m ON m.tenant_id=s.tenant_id AND m.user_id=?
              AND m.role IN ('OWNER','STAFF')
            JOIN identity_user u ON u.id=m.user_id AND u.status='ACTIVE' AND u.verified_at IS NOT NULL
            WHERE s.tenant_id=?
            """
                + (forUpdate ? " FOR UPDATE OF s FOR SHARE OF m, u" : " FOR SHARE OF s, m, u"),
            String.class,
            actor,
            tenant)
        .stream()
        .findFirst();
  }

  /**
   * Customers by name, at most {@code limit}. For the OWNER ({@code full}), also by e-mail or by
   * part of the phone (4 digits or more). An employee matches a phone only whole, so the masked
   * phone and the e-mail they never see cannot be guessed digit by digit.
   */
  public List<StoreCustomer> search(UUID tenant, String text, boolean full, int limit) {
    String like = like(text);
    String digits = text.replaceAll("[^0-9+]", "");
    String phone =
        full
            ? (digits.replace("+", "").length() >= 4 ? "%" + digits + "%" : null)
            : (digits.replace("+", "").length() >= 10 ? digits : null);
    return jdbc.query(
        SELECT
            + " WHERE tenant_id=? AND (name ILIKE ? OR email_key LIKE CAST(? AS text)"
            + " OR phone LIKE CAST(? AS text)) ORDER BY lower(name), id LIMIT ?",
        StoreCustomerRepository::customer,
        tenant,
        like,
        full ? like.toLowerCase(java.util.Locale.ROOT) : null,
        phone,
        limit);
  }

  public Optional<StoreCustomer> byId(UUID tenant, UUID id) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND id=?", StoreCustomerRepository::customer, tenant, id)
        .stream()
        .findFirst();
  }

  public Optional<StoreCustomer> byRequest(UUID tenant, UUID request) {
    return jdbc
        .query(
            SELECT + " WHERE tenant_id=? AND request_id=?",
            StoreCustomerRepository::customer,
            tenant,
            request)
        .stream()
        .findFirst();
  }

  /** Whether another customer of the store (not {@code except}) has this e-mail key. */
  public boolean emailTaken(UUID tenant, String emailKey, UUID except) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM store_customer WHERE tenant_id=? AND email_key=?"
                + " AND id IS DISTINCT FROM CAST(? AS uuid))",
            Boolean.class,
            tenant,
            emailKey,
            except));
  }

  public void insert(
      UUID tenant, UUID id, UUID request, CustomerInput.Data data, UUID actor, Instant now) {
    jdbc.update(
        """
        INSERT INTO store_customer(id,tenant_id,request_id,name,phone,email_original,email_key,
          note,credit_limit,created_by,created_at,updated_by,updated_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        tenant,
        request,
        data.name(),
        data.phone(),
        data.email(),
        data.emailKey(),
        data.note(),
        data.creditLimit(),
        actor,
        Timestamp.from(now),
        actor,
        Timestamp.from(now));
  }

  public boolean update(UUID tenant, UUID id, CustomerInput.Data data, UUID actor, Instant now) {
    return jdbc.update(
            """
            UPDATE store_customer SET name=?, phone=?, email_original=?, email_key=?, note=?,
              credit_limit=?, updated_by=?, updated_at=?
            WHERE tenant_id=? AND id=?
            """,
            data.name(),
            data.phone(),
            data.email(),
            data.emailKey(),
            data.note(),
            data.creditLimit(),
            actor,
            Timestamp.from(now),
            tenant,
            id)
        == 1;
  }

  private static final String SELECT =
      "SELECT id, request_id, name, phone, email_original, note, credit_limit, created_at"
          + " FROM store_customer";

  private static StoreCustomer customer(ResultSet row, int index) throws SQLException {
    BigDecimal limit = row.getBigDecimal("credit_limit");
    return new StoreCustomer(
        row.getObject("id", UUID.class),
        row.getObject("request_id", UUID.class),
        row.getString("name"),
        row.getString("phone"),
        row.getString("email_original"),
        row.getString("note"),
        limit == null ? null : limit.setScale(2),
        row.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  private static String like(String text) {
    return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
