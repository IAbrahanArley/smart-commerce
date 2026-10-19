package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V13 is additive: reservations created before orders existed (local fixtures) survive the new
 * order foreign key, which is still enforced for every new reservation. V16 validates that key only
 * when no such reservation remains, and never deletes one.
 */
@Testcontainers
class SalesOrderMigrationIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  @Test
  void migratingToV13KeepsPreexistingReservationsAndEnforcesTheOrderKeyForNewOnes() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    flyway(source, "12").migrate();
    var jdbc = new JdbcTemplate(source);
    UUID tenant = UUID.randomUUID();
    UUID variant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, created_at) VALUES (?, ?, 'Synthetic store', 'migracao-pedidos', now())",
        UUID.randomUUID(),
        tenant);
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at) SELECT ?,?,id,'Synthetic item','MIG-O',10.00,'BRL',now(),now() FROM store WHERE tenant_id=?",
        variant,
        tenant,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,currency,options_json,combination_key) VALUES (?,?,?,'MIG-O',10.00,'BRL','[]','')",
        variant,
        tenant,
        variant);
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES (?,?,5)",
        variant,
        tenant);
    String reservation =
        "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at) VALUES (?,?,?,?,2,'ACTIVE',now()+interval '30 minutes',now())";
    jdbc.update(reservation, UUID.randomUUID(), tenant, variant, UUID.randomUUID());
    List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM inventory_reservation");

    flyway(source, "latest").migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version='13'", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForList("SELECT * FROM inventory_reservation")).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT inventory_available_quantity(?,?,now())", Integer.class, tenant, variant))
        .isEqualTo(3);
    assertThatThrownBy(
            () -> jdbc.update(reservation, UUID.randomUUID(), tenant, variant, UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID order = TestOrders.create(jdbc, tenant);
    jdbc.update(reservation, UUID.randomUUID(), tenant, variant, order);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation", Long.class))
        .isEqualTo(2);
    // V16 never deletes the pre-order reservation: with it present the key stays NOT VALID.
    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version='16'", Boolean.class))
        .isTrue();
    assertThat(validated(jdbc)).isFalse();
  }

  @Test
  void v16ValidatesTheOrderKeyWhenEveryReservationHasAnOrder() {
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("CREATE DATABASE clean_v16");
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(?=\\?|$)", "/clean_v16"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    flyway(source, "latest").migrate();
    assertThat(validated(new JdbcTemplate(source))).isTrue();
  }

  private static boolean validated(JdbcTemplate jdbc) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT convalidated FROM pg_constraint WHERE conname='inventory_reservation_order_tenant_fk'",
            Boolean.class));
  }

  private static Flyway flyway(DriverManagerDataSource source, String target) {
    return Flyway.configure()
        .dataSource(source)
        .locations("classpath:db/migration")
        .cleanDisabled(true)
        .target(target)
        .load();
  }
}
