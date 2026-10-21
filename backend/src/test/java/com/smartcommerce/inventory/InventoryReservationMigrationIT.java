package com.smartcommerce.inventory;

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

/** V11 is additive: balances and ledger rows written under V10 survive unchanged. */
@Testcontainers
class InventoryReservationMigrationIT {
  private static final String LEDGER =
      "SELECT sequence,id,tenant_id,product_id,actor_id,request_id,type,delta,balance_after,reason,created_at FROM inventory_movement ORDER BY sequence";

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  @Test
  void migratingToV11PreservesBalancesAndMovements() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    flyway(source, "10").migrate();
    var jdbc = new JdbcTemplate(source);
    UUID user = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, 'mig@example.test', 'mig@example.test', 'ACTIVE', now(), now(), now())",
        user);
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, created_at) VALUES (?, ?, 'Synthetic store', 'migracao', now())",
        UUID.randomUUID(),
        tenant);
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,cost,currency,created_at,updated_at) SELECT ?,?,id,'Synthetic item','MIG',10.00,1.00,'BRL',now(),now() FROM store WHERE tenant_id=?",
        product,
        tenant,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,'MIG',10.00,1.00,'BRL','[]','')",
        product,
        tenant,
        product);
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES (?,?,7)",
        product,
        tenant);
    for (int[] movement : new int[][] {{10, 10}, {-3, 7}})
      jdbc.update(
          "INSERT INTO inventory_movement(id,tenant_id,product_id,actor_id,request_id,type,delta,balance_after,reason,created_at) VALUES (?,?,?,?,?,?,?,?,'Fixture',now())",
          UUID.randomUUID(),
          tenant,
          product,
          user,
          UUID.randomUUID(),
          movement[0] > 0 ? "ENTRY" : "ADJUSTMENT",
          movement[0],
          movement[1]);
    List<Map<String, Object>> balances = jdbc.queryForList("SELECT * FROM inventory_balance");
    List<Map<String, Object>> ledger =
        // Original V7 columns only: later migrations add columns (SC-014) without changing rows.
        jdbc.queryForList(LEDGER);

    flyway(source, "latest").migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version='11'", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForList("SELECT * FROM inventory_balance")).isEqualTo(balances);
    assertThat(jdbc.queryForList(LEDGER)).isEqualTo(ledger);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT inventory_available_quantity(?,?,now())", Integer.class, tenant, product))
        .isEqualTo(7);
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
