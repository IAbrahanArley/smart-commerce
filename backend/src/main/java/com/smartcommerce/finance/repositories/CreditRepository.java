package com.smartcommerce.finance.repositories;

import com.smartcommerce.finance.domain.Credit;
import com.smartcommerce.finance.domain.PageCursor;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Store credit (SC-055): installments, receipts and allocations. Every statement is scoped by the
 * tenant it is given, after the caller authorized the actor and locked the store.
 */
@Repository
public class CreditRepository {
  /** What was received of installment {@code i}. */
  private static final String RECEIVED =
      "COALESCE((SELECT SUM(a.amount) FROM credit_allocation a WHERE a.installment_id=i.id),0)";

  private final JdbcTemplate jdbc;

  public CreditRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertInstallment(
      UUID tenant,
      UUID sale,
      UUID customer,
      int number,
      LocalDate dueOn,
      BigDecimal amount,
      Instant now) {
    jdbc.update(
        """
        INSERT INTO credit_installment(id,tenant_id,counter_sale_id,customer_id,number,due_on,amount,
          created_at)
        VALUES (?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        tenant,
        sale,
        customer,
        number,
        Date.valueOf(dueOn),
        amount,
        Timestamp.from(now));
  }

  /**
   * The installments of the customer's active credit sales, oldest due first; only the open ones
   * when {@code openOnly}.
   */
  public List<Credit.Installment> installments(UUID tenant, UUID customer, boolean openOnly) {
    return jdbc.query(
        "SELECT * FROM ("
            + INSTALLMENTS
            + " WHERE i.tenant_id=? AND i.customer_id=? AND c.status='ACTIVE') x"
            + (openOnly ? " WHERE x.amount > x.received" : "")
            + " ORDER BY x.due_on, x.sold_at, x.number",
        CreditRepository::installment,
        tenant,
        customer);
  }

  /** What the customer owes now: their active credit sales' installments minus receipts. */
  public BigDecimal balance(UUID tenant, UUID customer) {
    return jdbc.queryForObject(
            "SELECT COALESCE(SUM(i.amount - "
                + RECEIVED
                + "),0) FROM credit_installment i JOIN counter_sale c ON c.id=i.counter_sale_id"
                + " AND c.tenant_id=i.tenant_id"
                + " WHERE i.tenant_id=? AND i.customer_id=? AND c.status='ACTIVE'",
            BigDecimal.class,
            tenant,
            customer)
        .setScale(2);
  }

  public Optional<LocalDate> firstDueOn(UUID tenant, UUID sale) {
    return jdbc
        .query(
            "SELECT due_on FROM credit_installment WHERE tenant_id=? AND counter_sale_id=?"
                + " AND number=1",
            (row, index) -> row.getDate(1).toLocalDate(),
            tenant,
            sale)
        .stream()
        .findFirst();
  }

  /** Whether every installment of the sale was received (the sale settles then). */
  public boolean salePaid(UUID tenant, UUID sale) {
    return !Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM credit_installment i WHERE i.tenant_id=?"
                + " AND i.counter_sale_id=? AND i.amount > "
                + RECEIVED
                + ")",
            Boolean.class,
            tenant,
            sale));
  }

  /** Whether some receipt already paid part of the sale (it can no longer be cancelled). */
  public boolean saleReceived(UUID tenant, UUID sale) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS(SELECT 1 FROM credit_allocation a
              JOIN credit_installment i ON i.id=a.installment_id AND i.tenant_id=a.tenant_id
              WHERE i.tenant_id=? AND i.counter_sale_id=?)
            """,
            Boolean.class,
            tenant,
            sale));
  }

  public Optional<Credit.Receipt> receiptByRequest(UUID tenant, UUID request) {
    return jdbc
        .query(
            """
            SELECT id, request_id, customer_id, amount, account_id, cash_session_id, received_on,
              created_at
            FROM credit_receipt WHERE tenant_id=? AND request_id=?
            """,
            (row, index) ->
                new Credit.Receipt(
                    row.getObject(1, UUID.class),
                    row.getObject(2, UUID.class),
                    row.getObject(3, UUID.class),
                    row.getBigDecimal(4).setScale(2),
                    row.getObject(5, UUID.class),
                    row.getObject(6, UUID.class),
                    row.getDate(7).toLocalDate(),
                    row.getObject(8, OffsetDateTime.class).toInstant()),
            tenant,
            request)
        .stream()
        .findFirst();
  }

  /** Inserts once per request id; returns whether this call created it. */
  public boolean insertReceipt(
      UUID tenant,
      UUID id,
      UUID request,
      UUID customer,
      BigDecimal amount,
      UUID account,
      UUID session,
      LocalDate receivedOn,
      UUID actor,
      Instant now) {
    return jdbc.update(
            """
            INSERT INTO credit_receipt(id,tenant_id,request_id,customer_id,amount,account_id,
              cash_session_id,received_on,created_by,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id, request_id) DO NOTHING
            """,
            id,
            tenant,
            request,
            customer,
            amount,
            account,
            session,
            Date.valueOf(receivedOn),
            actor,
            Timestamp.from(now))
        == 1;
  }

  public void insertAllocation(UUID tenant, UUID receipt, UUID installment, BigDecimal amount) {
    jdbc.update(
        "INSERT INTO credit_allocation(id,tenant_id,receipt_id,installment_id,amount)"
            + " VALUES (?,?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        receipt,
        installment,
        amount);
  }

  /**
   * The customer's credit sales and receipts, newest first, after {@code cursor} (the last line of
   * the previous page) when given, at most {@code limit}.
   */
  public List<Credit.Event> history(UUID tenant, UUID customer, PageCursor cursor, int limit) {
    List<Object> args = new ArrayList<>(List.of(tenant, customer, tenant, customer));
    String after = "";
    if (cursor != null) {
      after = " WHERE (h.at, h.id) < (?, ?)";
      args.add(utc(cursor.at()));
      args.add(cursor.id());
    }
    args.add(limit);
    return jdbc.query(
        """
        SELECT * FROM (
          SELECT 'SALE' AS kind, c.id, c.created_at AS at, c.total_amount AS amount,
            c.installment_count, c.status, NULL AS account_name, o.email_original AS actor_email
          FROM counter_sale c JOIN identity_user o ON o.id=c.created_by
          WHERE c.tenant_id=? AND c.customer_id=?
          UNION ALL
          SELECT 'RECEIPT', r.id, r.created_at, r.amount, NULL, NULL, a.name, u.email_original
          FROM credit_receipt r
          JOIN finance_account a ON a.id=r.account_id AND a.tenant_id=r.tenant_id
          JOIN identity_user u ON u.id=r.created_by
          WHERE r.tenant_id=? AND r.customer_id=?
        ) h"""
            + after
            + " ORDER BY h.at DESC, h.id DESC LIMIT ?",
        (row, index) ->
            new Credit.Event(
                row.getString("kind"),
                row.getObject("id", UUID.class),
                row.getObject("at", OffsetDateTime.class).toInstant(),
                row.getBigDecimal("amount").setScale(2),
                row.getObject("installment_count", Integer.class),
                row.getString("status"),
                row.getString("account_name"),
                row.getString("actor_email")),
        args.toArray());
  }

  /**
   * The store's customers, newest first, with what each has open and overdue on {@code today}; only
   * those with something open ({@code "OPEN"}) or overdue ({@code "OVERDUE"}) when asked; after
   * {@code cursor} when given, at most {@code limit}.
   */
  public List<Credit.CustomerLine> customers(
      UUID tenant, LocalDate today, String filter, PageCursor cursor, int limit) {
    List<Object> args = new ArrayList<>(List.of(Date.valueOf(today), tenant));
    StringBuilder where = new StringBuilder(" WHERE s.tenant_id=?");
    if ("OPEN".equals(filter)) where.append(" AND b.open > 0");
    if ("OVERDUE".equals(filter)) where.append(" AND b.overdue > 0");
    if (cursor != null) {
      where.append(" AND (s.created_at, s.id) < (?, ?)");
      args.add(utc(cursor.at()));
      args.add(cursor.id());
    }
    args.add(limit);
    return jdbc.query(
        """
        SELECT s.id, s.name, s.phone, s.credit_limit, s.created_at, b.open, b.overdue, b.next_due
        FROM store_customer s
        CROSS JOIN LATERAL (
          SELECT COALESCE(SUM(x.amount - x.received),0) AS open,
            COALESCE(SUM(x.amount - x.received) FILTER (WHERE x.due_on < ?),0) AS overdue,
            MIN(x.due_on) FILTER (WHERE x.amount > x.received) AS next_due
          FROM ("""
            + INSTALLMENTS
            + """
           WHERE i.tenant_id=s.tenant_id AND i.customer_id=s.id AND c.status='ACTIVE') x
        ) b"""
            + where
            + " ORDER BY s.created_at DESC, s.id DESC LIMIT ?",
        (row, index) -> {
          BigDecimal creditLimit = row.getBigDecimal("credit_limit");
          Date next = row.getDate("next_due");
          return new Credit.CustomerLine(
              row.getObject("id", UUID.class),
              row.getString("name"),
              row.getString("phone"),
              creditLimit == null ? null : creditLimit.setScale(2),
              row.getObject("created_at", OffsetDateTime.class).toInstant(),
              row.getBigDecimal("open").setScale(2),
              row.getBigDecimal("overdue").setScale(2),
              next == null ? null : next.toLocalDate());
        },
        args.toArray());
  }

  /** The store's open and overdue totals on {@code today}, over every customer. */
  public Credit.Totals totals(UUID tenant, LocalDate today) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(x.amount - x.received),0),"
            + " COALESCE(SUM(x.amount - x.received) FILTER (WHERE x.due_on < ?),0),"
            + " COUNT(DISTINCT x.customer_id) FILTER (WHERE x.due_on < ? AND x.amount > x.received)"
            + " FROM ("
            + INSTALLMENTS
            + " WHERE i.tenant_id=? AND c.status='ACTIVE') x",
        (row, index) ->
            new Credit.Totals(
                row.getBigDecimal(1).setScale(2), row.getBigDecimal(2).setScale(2), row.getLong(3)),
        Date.valueOf(today),
        Date.valueOf(today),
        tenant);
  }

  private static final String INSTALLMENTS =
      "SELECT i.id, i.counter_sale_id, c.created_at AS sold_at, i.customer_id, i.number,"
          + " c.installment_count, i.due_on, i.amount, "
          + RECEIVED
          + " AS received FROM credit_installment i"
          + " JOIN counter_sale c ON c.id=i.counter_sale_id AND c.tenant_id=i.tenant_id";

  private static Credit.Installment installment(ResultSet row, int index) throws SQLException {
    return new Credit.Installment(
        row.getObject("id", UUID.class),
        row.getObject("counter_sale_id", UUID.class),
        row.getObject("sold_at", OffsetDateTime.class).toInstant(),
        row.getInt("number"),
        row.getInt("installment_count"),
        row.getDate("due_on").toLocalDate(),
        row.getBigDecimal("amount").setScale(2),
        row.getBigDecimal("received").setScale(2));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
