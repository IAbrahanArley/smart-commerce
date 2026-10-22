package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * SC-019: V22 is additive and fills the ledger for orders paid or refunded before it existed, with
 * the same keys the code uses, signed amounts, COGS from the item cost snapshots (incomplete when
 * one is missing) and the COGS reversal only when the goods came back or never left.
 */
@Testcontainers
class FinanceLedgerMigrationIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  JdbcTemplate jdbc;
  UUID tenant;
  UUID user;
  UUID variant;
  UUID product;

  @Test
  void migratingToV22RecordsExistingSalesRefundsAndCostsOnce() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    flyway(source, "21").migrate();
    jdbc = new JdbcTemplate(source);
    fixtures();
    UUID paid = order("PAID", false, "3.00", 2); // 2 × 10.00 + 5.00 shipping, cost 2 × 3.00
    UUID costless = order("PAID", false, null, 1);
    UUID stockless = order("REFUNDED", true, "3.00", 1); // never deducted: cost reversed
    UUID kept = order("REFUNDED", false, "3.00", 1); // refund without restock: cost stays
    UUID restocked = order("REFUNDED", false, "3.00", 1);
    refund(restocked, true);
    UUID pending = order("PENDING_PAYMENT", false, "3.00", 1);

    flyway(source, "latest").migrate();

    assertThat(entries(paid))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("sale:" + paid, "SALE 25.00", "cogs:" + paid, "COGS -6.00 false"));
    assertThat(entries(costless))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("sale:" + costless, "SALE 15.00", "cogs:" + costless, "COGS 0 true"));
    assertThat(entries(stockless))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "sale:" + stockless, "SALE 15.00",
                "cogs:" + stockless, "COGS -3.00 false",
                "refund:" + stockless, "REFUND -15.00",
                "cogs-reversal:" + stockless, "COGS_REVERSAL 3.00 false"));
    assertThat(entries(kept)).containsOnlyKeys("sale:" + kept, "cogs:" + kept, "refund:" + kept);
    assertThat(entries(restocked)).containsKey("cogs-reversal:" + restocked);
    assertThat(entries(pending)).isEmpty();
    // The refund counts when it happened, the sale when it was paid (F1).
    assertThat(
            jdbc.queryForObject(
                "SELECT bool_and(e.competence_at = CASE WHEN e.type IN ('REFUND','COGS_REVERSAL')"
                    + " THEN o.refunded_at ELSE o.paid_at END)"
                    + " FROM finance_entry e JOIN sales_order o ON o.id=e.order_id",
                Boolean.class))
        .isTrue();
  }

  private Map<String, String> entries(UUID order) {
    return jdbc
        .queryForList(
            "SELECT entry_key,type,amount,cost_incomplete FROM finance_entry WHERE order_id=?",
            order)
        .stream()
        .collect(
            Collectors.toMap(
                row -> row.get("entry_key").toString(),
                row ->
                    row.get("type")
                        + " "
                        + row.get("amount")
                        + (row.get("type").toString().startsWith("COGS")
                            ? " " + row.get("cost_incomplete")
                            : "")));
  }

  private void fixtures() {
    tenant = UUID.randomUUID();
    user = UUID.randomUUID();
    product = UUID.randomUUID();
    variant = product;
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, 'fin@example.test', 'fin@example.test', 'ACTIVE', now(), now(), now())",
        user);
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, created_at) VALUES (?, ?, 'Synthetic store', 'financeiro', now())",
        UUID.randomUUID(),
        tenant);
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at) SELECT ?,?,id,'Item','FIN',10.00,'BRL',now(),now() FROM store WHERE tenant_id=?",
        product,
        tenant,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,'FIN',10.00,NULL,'BRL','[]','')",
        variant,
        tenant,
        product);
  }

  private UUID order(String status, boolean withoutStock, String cost, int quantity) {
    UUID id = UUID.randomUUID();
    boolean refunded = "REFUNDED".equals(status);
    boolean paid = refunded || "PAID".equals(status);
    String subtotal = String.format("%d.00", 10 * quantity);
    String total = String.format("%d.00", 10 * quantity + 5);
    jdbc.update(
        """
        INSERT INTO sales_order(id,tenant_id,store_id,number,status,currency,subtotal_amount,
          shipping_amount,discount_amount,total_amount,fulfillment_type,fulfillment_text,
          buyer_name,buyer_email,buyer_email_key,buyer_phone,reservation_expires_at,request_id,
          request_fingerprint,access_token_hash,created_at,updated_at,paid_at,refunded_at,
          paid_without_stock)
        SELECT ?,s.tenant_id,s.id,?,?, 'BRL',?::numeric,5.00,0,?::numeric,'PICKUP','Retirada',
          'Comprador','b@example.test','b@example.test','11999990000',now()+interval '30 minutes',
          ?,repeat('0',64),repeat('0',64),now()-interval '2 days',now(),
          CASE WHEN ? THEN now()-interval '2 days' END, CASE WHEN ? THEN now()-interval '1 day' END, ?
        FROM store s WHERE s.tenant_id=?
        """,
        id,
        java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
        status,
        subtotal,
        total,
        UUID.randomUUID(),
        paid,
        refunded,
        withoutStock,
        tenant);
    jdbc.update(
        """
        INSERT INTO sales_order_item(id,tenant_id,order_id,variant_id,product_id,position,
          product_name_snapshot,variant_options_snapshot,sku_snapshot,unit_price_snapshot,
          unit_cost_snapshot,quantity,line_total)
        VALUES (?,?,?,?,?,0,'Item','[]','FIN',10.00,?::numeric,?,?::numeric)
        """,
        UUID.randomUUID(),
        tenant,
        id,
        variant,
        product,
        cost,
        quantity,
        subtotal);
    return id;
  }

  private void refund(UUID order, boolean restock) {
    UUID payment = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO payment(id,tenant_id,order_id,provider,status,currency,amount,provider_user_id,
          provider_order_id,checkout_url,provider_expires_at,paid_amount,approved_at,created_at,
          updated_at)
        SELECT ?,tenant_id,id,'MERCADO_PAGO','REFUNDED','BRL',total_amount,'111111',?,
          'https://www.mercadopago.com.br/c',now(),total_amount,now(),now(),now()
        FROM sales_order WHERE id=?
        """,
        payment,
        "ORD" + payment.toString().substring(0, 8),
        order);
    jdbc.update(
        """
        INSERT INTO payment_refund(id,tenant_id,payment_id,order_id,status,amount,reason,restock,
          requested_by,requested_at,confirmed_at)
        VALUES (?,?,?,?,'CONFIRMED',15.00,'Motivo sintético',?,?,now(),now())
        """,
        UUID.randomUUID(),
        tenant,
        payment,
        order,
        restock,
        user);
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
