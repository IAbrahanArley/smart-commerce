package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * SC-051 (ADR-0019 C4): migrating to V42 turns existing expenses into payables without touching the
 * ledger. Unpaid ones fall due on their competence date; paid ones keep their date with no account
 * ("conta não informada").
 */
@Testcontainers
class PayablesMigrationIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  @Test
  void existingExpensesBecomePayablesAndTheLedgerStaysTheSame() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    flyway(source, "41").migrate();
    var jdbc = new JdbcTemplate(source);
    UUID user = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at,"
            + " verified_at) VALUES (?, 'migracao@example.test', 'migracao@example.test', 'ACTIVE',"
            + " now(), now(), now())",
        user);
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    UUID open = expense(jdbc, tenant, user, "2026-09-05", null, "ACTIVE");
    UUID paid = expense(jdbc, tenant, user, "2026-09-10", "2026-09-12", "ACTIVE");
    UUID cancelled = expense(jdbc, tenant, user, "2026-08-01", null, "CANCELLED");
    List<Map<String, Object>> ledger =
        jdbc.queryForList("SELECT * FROM finance_entry ORDER BY entry_key");

    flyway(source, "42").migrate();

    assertThat(jdbc.queryForList("SELECT * FROM finance_entry ORDER BY entry_key"))
        .isEqualTo(ledger);
    assertThat(row(jdbc, open)).isEqualTo("2026-09-05 null null NONE 0 ACTIVE");
    assertThat(row(jdbc, paid)).isEqualTo("2026-09-10 2026-09-12 null NONE 0 ACTIVE");
    assertThat(row(jdbc, cancelled)).isEqualTo("2026-08-01 null null NONE 0 CANCELLED");
  }

  private static String row(JdbcTemplate jdbc, UUID id) {
    return jdbc.queryForObject(
        "SELECT concat_ws(' ', due_date::text, coalesce(paid_on::text,'null'),"
            + " coalesce(paid_account_id::text,'null'), recurrence, surcharge_amount::text, status)"
            + " FROM expense WHERE id=?",
        String.class,
        id);
  }

  private static UUID expense(
      JdbcTemplate jdbc, UUID tenant, UUID user, String competence, String paid, String status) {
    UUID id = UUID.randomUUID();
    boolean cancelled = "CANCELLED".equals(status);
    jdbc.update(
        """
        INSERT INTO expense(id,tenant_id,request_id,category,kind,description,amount,
          competence_date,paid_on,status,created_by,created_at,cancelled_by,cancelled_at,
          cancel_reason)
        VALUES (?,?,?,'RENT','FIXED','Aluguel',100.00,?::date,?::date,?,?,now(),?,?,?)
        """,
        id,
        tenant,
        UUID.randomUUID(),
        competence,
        paid,
        status,
        user,
        cancelled ? user : null,
        cancelled ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
        cancelled ? "Motivo sintético" : null);
    jdbc.update(
        """
        INSERT INTO finance_entry(id,tenant_id,entry_key,type,amount,currency,competence_at,
          settled_at,expense_id,created_at)
        VALUES (?,?,?,'EXPENSE',-100.00,'BRL',?::date::timestamptz,?::date::timestamptz,?,now())
        """,
        UUID.randomUUID(),
        tenant,
        "expense:" + id,
        competence,
        paid,
        id);
    return id;
  }

  private static Flyway flyway(DriverManagerDataSource source, String target) {
    return Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/migration")
        .target(target)
        .cleanDisabled(true)
        .load();
  }
}
