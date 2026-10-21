package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.smartcommerce.orders.services.OrderLifecycleService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SC-010: expiration routine, OWNER list/detail/cancel, D2 anonymization and A/B isolation. The
 * scheduler is off; each test drives the batches directly. Synthetic data only.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orders.maintenance.enabled=false",
      "orders.checkout.enabled=true",
      "deployment.environment=hosted-test",
      "orders.client-ip.secret=synthetic-forwarding-secret-for-tests-only-0002"
    })
class OrderLifecycleIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic orders lifecycle secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrderLifecycleService lifecycle;
  UUID ownerA;
  UUID tenantA;
  UUID tenantB;
  UUID variantA;
  UUID variantB;
  long nextNumber;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = identity("pedidos-a@example.test");
    UUID ownerB = identity("pedidos-b@example.test");
    tenantA = store(ownerA, "pedidos-a");
    tenantB = store(ownerB, "pedidos-b");
    variantA = variant(tenantA, "SKU-A", 5);
    variantB = variant(tenantB, "SKU-B", 5);
    nextNumber = 1;
  }

  @Test
  void ownerListsReadsAndCancelsReleasingStockOnceAndBuyerSeesCancelled() throws Exception {
    UUID order = order(tenantA, variantA, 2, "PENDING_PAYMENT", "30 minutes", "0 days", true);
    assertThat(available(tenantA, variantA)).isEqualTo(3);
    Browser a = login("pedidos-a@example.test");

    JsonNode list = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(list.get("orders")).hasSize(1);
    assertThat(list.get("orders").get(0).get("buyerName").asString()).isEqualTo("Comprador A");
    assertThat(list.get("orders").get(0).get("units").asInt()).isEqualTo(2);
    var detailResponse = a.get(path(tenantA) + "/" + order);
    assertThat(detailResponse.headers().firstValue("Cache-Control").orElse(""))
        .contains("no-store");
    JsonNode detail = JSON.readTree(detailResponse.body());
    assertThat(detail.get("status").asString()).isEqualTo("PENDING_PAYMENT");
    assertThat(detail.get("buyer").get("email").asString()).isEqualTo("a@example.test");
    assertThat(detail.get("address").get("postalCode").asString()).isEqualTo("01001000");
    assertThat(detail.get("items").get(0).get("sku").asString()).isEqualTo("SKU-A");
    assertThat(detail.get("items").get(0).get("unitCost").asString()).isEqualTo("4.00");
    assertThat(detail.get("total").asString()).isEqualTo("32.50");

    assertThat(a.post(cancel(tenantA, order), Map.of("reason", "  ")).statusCode()).isEqualTo(400);
    assertThat(a.post(cancel(tenantA, order), Map.of("reason", "ab")).statusCode()).isEqualTo(400);
    assertThat(a.post(cancel(tenantA, order), Map.of("reason", "a\nb c")).statusCode())
        .isEqualTo(400);
    assertThat(a.post(cancel(tenantA, order), Map.of("reason", "x".repeat(301))).statusCode())
        .isEqualTo(400);
    var cancelled = a.post(cancel(tenantA, order), Map.of("reason", "Sem estoque físico"));
    assertThat(cancelled.statusCode()).isEqualTo(200);
    JsonNode body = JSON.readTree(cancelled.body());
    assertThat(body.get("status").asString()).isEqualTo("CANCELLED");
    assertThat(body.get("cancellation").get("reason").asString()).isEqualTo("Sem estoque físico");
    assertThat(available(tenantA, variantA)).isEqualTo(5);
    assertThat(reservationStatus(order)).isEqualTo("RELEASED");

    // Repeating has no second effect: same cancellation, reason unchanged, nothing re-released.
    var again = a.post(cancel(tenantA, order), Map.of("reason", "Outro motivo qualquer"));
    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(again.body()).get("cancellation")).isEqualTo(body.get("cancellation"));
    assertThat(
            jdbc.queryForObject(
                "SELECT cancelled_by FROM sales_order WHERE id=?", UUID.class, order))
        .isEqualTo(ownerA);
    assertThat(available(tenantA, variantA)).isEqualTo(5);
    assertThat(publicStatus(order)).isEqualTo("CANCELLED");
  }

  @Test
  void expiredOrdersCannotBeCancelledAndTheRoutineRecordsThemOnce() throws Exception {
    UUID due = order(tenantA, variantA, 1, "PENDING_PAYMENT", "-1 minutes", "0 days", false);
    UUID live = order(tenantA, variantA, 1, "PENDING_PAYMENT", "30 minutes", "0 days", false);
    UUID otherDue = order(tenantB, variantB, 2, "PENDING_PAYMENT", "-5 minutes", "0 days", false);
    Browser a = login("pedidos-a@example.test");
    var rejected = a.post(cancel(tenantA, due), Map.of("reason", "Cliente desistiu"));
    assertThat(rejected.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(rejected.body()).get("code").asString()).isEqualTo("ORDER_EXPIRED");
    assertThat(status(due)).isEqualTo("PENDING_PAYMENT");
    assertThat(JSON.readTree(a.get(path(tenantA) + "/" + due).body()).get("status").asString())
        .isEqualTo("EXPIRED");

    assertThat(lifecycle.expireDue(100)).isEqualTo(2);
    assertThat(status(due)).isEqualTo("EXPIRED");
    assertThat(status(otherDue)).isEqualTo("EXPIRED");
    assertThat(status(live)).isEqualTo("PENDING_PAYMENT");
    assertThat(reservationStatus(due)).isEqualTo("EXPIRED");
    assertThat(reservationStatus(otherDue)).isEqualTo("EXPIRED");
    assertThat(reservationStatus(live)).isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT expired_at IS NOT NULL FROM sales_order WHERE id=?", Boolean.class, due))
        .isTrue();
    assertThat(lifecycle.expireDue(100)).isZero();
    assertThat(available(tenantA, variantA)).isEqualTo(4);
  }

  @Test
  void concurrentRoutinesSplitTheWorkAndNeverExpireTwice() throws Exception {
    for (int i = 0; i < 30; i++)
      order(tenantA, variantA, 1, "PENDING_PAYMENT", "-1 minutes", "0 days", false);
    var barrier = new CyclicBarrier(4);
    List<Callable<Integer>> tasks = new ArrayList<>();
    for (int i = 0; i < 4; i++)
      tasks.add(
          () -> {
            barrier.await(20, TimeUnit.SECONDS);
            int total = 0;
            for (int n; (n = lifecycle.expireDue(5)) > 0; ) total += n;
            return total;
          });
    int sum = 0;
    try (var pool = Executors.newFixedThreadPool(4)) {
      for (var future : pool.invokeAll(tasks, 90, TimeUnit.SECONDS)) sum += future.get();
    }
    assertThat(sum).isEqualTo(30);
    assertThat(count("SELECT count(*) FROM sales_order WHERE status='EXPIRED'")).isEqualTo(30);
    assertThat(count("SELECT count(*) FROM inventory_reservation WHERE status='EXPIRED'"))
        .isEqualTo(30);
  }

  @Test
  void cancellationRacingTheRoutineHasExactlyOneOutcome() throws Exception {
    UUID due = order(tenantA, variantA, 1, "PENDING_PAYMENT", "-1 minutes", "0 days", false);
    UUID live = order(tenantA, variantA, 1, "PENDING_PAYMENT", "30 minutes", "0 days", false);
    Browser a = login("pedidos-a@example.test");
    var barrier = new CyclicBarrier(3);
    List<Callable<String>> tasks =
        List.of(
            () -> {
              barrier.await(20, TimeUnit.SECONDS);
              return "cancel-due:"
                  + a.post(cancel(tenantA, due), Map.of("reason", "Corrida A")).statusCode();
            },
            () -> {
              barrier.await(20, TimeUnit.SECONDS);
              return "cancel-live:"
                  + a.post(cancel(tenantA, live), Map.of("reason", "Corrida B")).statusCode();
            },
            () -> {
              barrier.await(20, TimeUnit.SECONDS);
              return "expire:" + lifecycle.expireDue(100);
            });
    List<String> outcomes = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(3)) {
      for (var future : pool.invokeAll(tasks, 90, TimeUnit.SECONDS)) outcomes.add(future.get());
    }
    assertThat(outcomes).contains("cancel-due:409", "cancel-live:200");
    assertThat(status(live)).isEqualTo("CANCELLED");
    assertThat(reservationStatus(live)).isEqualTo("RELEASED");
    // The due order ends EXPIRED whether the routine ran before or after the rejected cancel.
    lifecycle.expireDue(100);
    assertThat(status(due)).isEqualTo("EXPIRED");
    assertThat(reservationStatus(due)).isEqualTo("EXPIRED");
  }

  @Test
  void cancellationRacingACheckoutForTheReleasedUnitsNeverOversells() throws Exception {
    jdbc.update("UPDATE inventory_balance SET quantity=2 WHERE product_id=?", variantA);
    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id=?", tenantA);
    jdbc.update(
        """
        INSERT INTO store_fulfillment(store_id,tenant_id,pickup_enabled,pickup_instructions,
          fixed_shipping_enabled,updated_at)
        SELECT id,tenant_id,true,'Retirada sintética',false,now() FROM store WHERE tenant_id=?
        """,
        tenantA);
    UUID held = order(tenantA, variantA, 2, "PENDING_PAYMENT", "30 minutes", "0 days", false);
    Browser a = login("pedidos-a@example.test");
    String checkout =
        JSON.writeValueAsString(
            Map.of(
                "requestId", UUID.randomUUID().toString(),
                "items", List.of(Map.of("variantId", variantA.toString(), "quantity", 2)),
                "fulfillment", Map.of("type", "PICKUP"),
                "buyer",
                    Map.of(
                        "name", "Outro comprador",
                        "email", "outro@example.test",
                        "phone", "11988887777"),
                "expectedTotal", "20.00"));
    var barrier = new CyclicBarrier(2);
    List<Callable<Integer>> tasks =
        List.of(
            () -> {
              barrier.await(20, TimeUnit.SECONDS);
              return a.post(cancel(tenantA, held), Map.of("reason", "Libera estoque")).statusCode();
            },
            () -> {
              barrier.await(20, TimeUnit.SECONDS);
              return new Browser()
                  .send("POST", "/api/public/stores/pedidos-a/orders", checkout, null)
                  .statusCode();
            });
    List<Integer> outcomes = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      for (var future : pool.invokeAll(tasks, 90, TimeUnit.SECONDS)) outcomes.add(future.get());
    }
    assertThat(outcomes.get(0)).isEqualTo(200);
    // The checkout either ran before the release (no stock) or after it (gets both units).
    assertThat(outcomes.get(1)).isIn(201, 409);
    assertThat(status(held)).isEqualTo("CANCELLED");
    long reserved =
        count(
            "SELECT COALESCE(sum(quantity),0) FROM inventory_reservation WHERE status='ACTIVE'"
                + " AND expires_at>now()");
    assertThat(reserved).isEqualTo(outcomes.get(1) == 201 ? 2 : 0);
    assertThat(available(tenantA, variantA)).isEqualTo(outcomes.get(1) == 201 ? 0 : 2);
  }

  @Test
  void ownersNeverSeeOrChangeAnotherTenantsOrders() throws Exception {
    UUID orderA = order(tenantA, variantA, 1, "PENDING_PAYMENT", "30 minutes", "0 days", true);
    order(tenantB, variantB, 1, "PENDING_PAYMENT", "30 minutes", "0 days", true);
    Browser b = login("pedidos-b@example.test");
    var crossList = b.get(path(tenantA));
    var absentList = b.get(path(UUID.randomUUID()));
    assertThat(crossList.statusCode()).isEqualTo(404);
    assertThat(crossList.body()).isEqualTo(absentList.body());
    var crossRead = b.get(path(tenantA) + "/" + orderA);
    var foreignInOwnStore = b.get(path(tenantB) + "/" + orderA);
    assertThat(crossRead.statusCode()).isEqualTo(404);
    assertThat(foreignInOwnStore.statusCode()).isEqualTo(404);
    assertThat(crossRead.body()).isEqualTo(foreignInOwnStore.body()).doesNotContain("Comprador");
    assertThat(b.post(cancel(tenantA, orderA), Map.of("reason", "Invasão")).statusCode())
        .isEqualTo(404);
    assertThat(b.post(cancel(tenantB, orderA), Map.of("reason", "Invasão")).statusCode())
        .isEqualTo(404);
    assertThat(status(orderA)).isEqualTo("PENDING_PAYMENT");
    JsonNode ownList = JSON.readTree(b.get(path(tenantB)).body()).get("orders");
    assertThat(ownList).hasSize(1);
    assertThat(ownList.get(0).get("buyerName").asString()).isEqualTo("Comprador B");

    Browser a = login("pedidos-a@example.test");
    String payload = JSON.writeValueAsString(Map.of("reason", "Sem CSRF"));
    assertThat(a.send("POST", cancel(tenantA, orderA), payload, null).statusCode()).isEqualTo(403);
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);
    assertThat(a.get(path(tenantA) + "?status=CHARGEBACK").statusCode()).isEqualTo(400);
    assertThat(a.get(path(tenantA) + "?after=-1").statusCode()).isEqualTo(400);
    assertThat(a.get(path(tenantA) + "/not-a-uuid").statusCode()).isEqualTo(400);
    jdbc.update("DELETE FROM tenant_membership WHERE tenant_id=? AND user_id=?", tenantA, ownerA);
    assertThat(a.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(a.post(cancel(tenantA, orderA), Map.of("reason", "Sem vínculo")).statusCode())
        .isEqualTo(404);
  }

  @Test
  void listFiltersByEffectiveStatusAndPagesByNumberCursor() throws Exception {
    for (int i = 0; i < 21; i++)
      order(tenantA, variantA, 0, "PENDING_PAYMENT", "30 minutes", "0 days", false);
    UUID pastDeadline =
        order(tenantA, variantA, 0, "PENDING_PAYMENT", "-1 minutes", "0 days", false);
    UUID expired = order(tenantA, variantA, 0, "EXPIRED", "-1 days", "2 days", false);
    UUID cancelled = order(tenantA, variantA, 0, "CANCELLED", "-1 days", "2 days", false);
    for (int i = 0; i < 3; i++)
      order(tenantB, variantB, 0, "PENDING_PAYMENT", "30 minutes", "0 days", false);
    Browser a = login("pedidos-a@example.test");

    JsonNode first = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(first.get("orders")).hasSize(20);
    assertThat(first.get("orders").get(0).get("number").asLong()).isEqualTo(24);
    String cursor = first.get("nextCursor").asString();
    JsonNode second = JSON.readTree(a.get(path(tenantA) + "?after=" + cursor).body());
    assertThat(second.get("orders")).hasSize(4);
    assertThat(second.get("nextCursor").isNull()).isTrue();
    List<String> ids = new ArrayList<>();
    for (JsonNode page : List.of(first, second))
      page.get("orders").forEach(row -> ids.add(row.get("id").asString()));
    assertThat(ids).hasSize(24).doesNotHaveDuplicates();

    assertThat(ids(a, "EXPIRED"))
        .containsExactlyInAnyOrder(pastDeadline.toString(), expired.toString());
    assertThat(ids(a, "CANCELLED")).containsExactly(cancelled.toString());
    assertThat(ids(a, "PENDING_PAYMENT")).hasSize(20).doesNotContain(pastDeadline.toString());
  }

  @Test
  void anonymizationRemovesBuyerDataFromOldClosedOrdersOnly() throws Exception {
    UUID oldExpired = order(tenantA, variantA, 1, "EXPIRED", "-91 days", "91 days", true);
    UUID oldCancelled = order(tenantA, variantA, 1, "CANCELLED", "-95 days", "95 days", false);
    UUID recentExpired = order(tenantA, variantA, 1, "EXPIRED", "-10 days", "10 days", true);
    UUID otherOld = order(tenantB, variantB, 1, "EXPIRED", "-100 days", "100 days", true);

    assertThat(lifecycle.anonymizeDue(100)).isEqualTo(3);
    for (UUID gone : List.of(oldExpired, oldCancelled, otherOld))
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT buyer_name IS NULL AND buyer_email IS NULL AND buyer_email_key IS NULL
                    AND buyer_phone IS NULL AND address_line1 IS NULL AND address_postal_code IS NULL
                    AND anonymized_at IS NOT NULL AND request_fingerprint=repeat('0',64)
                    AND total_amount=32.50 FROM sales_order WHERE id=?
                  """,
                  Boolean.class,
                  gone))
          .isTrue();
    // The store's free-text reason may quote the buyer: it is replaced, keeping the CHECKs valid.
    assertThat(
            jdbc.queryForObject(
                "SELECT cancel_reason FROM sales_order WHERE id=?", String.class, oldCancelled))
        .isEqualTo("Motivo removido na anonimização");
    assertThat(count("SELECT count(*) FROM sales_order_item WHERE order_id='" + oldExpired + "'"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT buyer_email FROM sales_order WHERE id=?", String.class, recentExpired))
        .isEqualTo("a@example.test");
    assertThat(lifecycle.anonymizeDue(100)).isZero();

    Browser a = login("pedidos-a@example.test");
    JsonNode detail = JSON.readTree(a.get(path(tenantA) + "/" + oldExpired).body());
    assertThat(detail.get("buyer").isNull()).isTrue();
    assertThat(detail.get("address").isNull()).isTrue();
    assertThat(detail.get("anonymizedAt").isNull()).isFalse();
    assertThat(detail.get("items").get(0).get("productName").asString()).isEqualTo("Produto A");
    assertThat(detail.toString()).doesNotContain("a@example.test", "Comprador");
  }

  @Test
  void schemaKeepsCancellationAndAnonymizationConsistentEvenThroughDirectSql() {
    UUID pending = order(tenantA, variantA, 0, "PENDING_PAYMENT", "30 minutes", "0 days", true);
    UUID expired = order(tenantA, variantA, 0, "EXPIRED", "-1 days", "2 days", true);
    // Pending orders keep their buyer data; anonymization must remove all of it at once.
    assertThatSqlFails(
        "UPDATE sales_order SET buyer_name=NULL,buyer_email=NULL,buyer_email_key=NULL,"
            + "buyer_phone=NULL,address_line1=NULL,address_district=NULL,address_city=NULL,"
            + "address_state=NULL,address_postal_code=NULL,anonymized_at=now() WHERE id=?",
        pending);
    assertThatSqlFails(
        "UPDATE sales_order SET buyer_name=NULL,anonymized_at=now() WHERE id=?", expired);
    assertThatSqlFails("UPDATE sales_order SET buyer_phone=NULL WHERE id=?", expired);
    // Cancellation needs actor, instant and reason; other states cannot carry them.
    assertThatSqlFails("UPDATE sales_order SET status='CANCELLED' WHERE id=?", pending);
    assertThatSqlFails(
        "UPDATE sales_order SET cancelled_at=now(),cancelled_by=(SELECT id FROM identity_user"
            + " LIMIT 1),cancel_reason='Motivo' WHERE id=?",
        expired);
    assertThatSqlFails(
        "UPDATE sales_order SET status='CANCELLED',cancelled_at=now(),cancelled_by=(SELECT id"
            + " FROM identity_user LIMIT 1),cancel_reason='ab' WHERE id=?",
        pending);
    assertThatSqlFails("UPDATE sales_order SET expired_at=now() WHERE id=?", pending);
    assertThat(status(pending)).isEqualTo("PENDING_PAYMENT");
  }

  private void assertThatSqlFails(String sql, UUID order) {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(sql, order))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  // --- helpers ---------------------------------------------------------------------------

  private List<String> ids(Browser browser, String status) throws Exception {
    List<String> ids = new ArrayList<>();
    JSON.readTree(browser.get(path(tenantA) + "?status=" + status).body())
        .get("orders")
        .forEach(row -> ids.add(row.get("id").asString()));
    return ids;
  }

  /**
   * Inserts a consistent order: header, one item and, when {@code quantity > 0}, its reservation.
   * {@code deadline} and {@code age} are PostgreSQL intervals relative to now.
   */
  private UUID order(
      UUID tenant,
      UUID variant,
      int quantity,
      String status,
      String deadline,
      String age,
      boolean shipping) {
    UUID id = UUID.randomUUID();
    String buyer = tenant.equals(tenantA) ? "A" : "B";
    String email = buyer.toLowerCase() + "@example.test";
    jdbc.update(
        """
        INSERT INTO sales_order(id,tenant_id,store_id,number,status,currency,subtotal_amount,
          shipping_amount,discount_amount,total_amount,fulfillment_type,fulfillment_text,
          buyer_name,buyer_email,buyer_email_key,buyer_phone,address_line1,address_district,
          address_city,address_state,address_postal_code,reservation_expires_at,request_id,
          request_fingerprint,access_token_hash,created_at,updated_at,cancelled_at,cancelled_by,
          cancel_reason)
        SELECT ?,s.tenant_id,s.id,?,?,'BRL',20.00,12.50,0,32.50,?,'Entrega sintética',?,?,?,
          '11999990000',?,?,?,?,?,now()+?::interval,?,repeat('a',64),repeat('b',64),
          now()-?::interval-interval '10 minutes',now(),?,?,?
        FROM store s WHERE s.tenant_id=?
        """,
        id,
        tenant.equals(tenantA) ? nextNumber++ : 1000 + nextNumber++,
        status,
        shipping ? "FIXED_SHIPPING" : "PICKUP",
        "Comprador " + buyer,
        email,
        email,
        shipping ? "Rua Sintética, 1" : null,
        shipping ? "Centro" : null,
        shipping ? "São Paulo" : null,
        shipping ? "SP" : null,
        shipping ? "01001000" : null,
        // Created 10 minutes before "age", so every deadline used here stays after creation.
        deadline,
        UUID.randomUUID(),
        age,
        "CANCELLED".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
        "CANCELLED".equals(status) ? ownerA : null,
        "CANCELLED".equals(status) ? "Motivo sintético" : null,
        tenant);
    // Keep the store counter ahead of fixture numbers, as a real checkout would.
    jdbc.update(
        """
        INSERT INTO sales_order_counter(store_id,tenant_id,last_number)
        SELECT store_id,tenant_id,number FROM sales_order WHERE id=?
        ON CONFLICT (store_id) DO UPDATE
          SET last_number=GREATEST(sales_order_counter.last_number,EXCLUDED.last_number)
        """,
        id);
    jdbc.update(
        """
        INSERT INTO sales_order_item(id,tenant_id,order_id,variant_id,product_id,position,
          product_name_snapshot,variant_options_snapshot,sku_snapshot,unit_price_snapshot,
          unit_cost_snapshot,quantity,line_total)
        SELECT ?,?,?,v.id,v.product_id,0,'Produto ' || ?,'[]',v.sku,10.00,4.00,2,20.00
        FROM catalog_variant v WHERE v.id=?
        """,
        UUID.randomUUID(),
        tenant,
        id,
        buyer,
        variant);
    if (quantity > 0)
      jdbc.update(
          """
          INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,
            expires_at,created_at,closed_at)
          SELECT ?,?,?,o.id,?,CASE WHEN o.status='PENDING_PAYMENT' THEN 'ACTIVE' ELSE 'EXPIRED' END,
            o.reservation_expires_at,o.created_at,
            CASE WHEN o.status='PENDING_PAYMENT' THEN NULL ELSE now() END
          FROM sales_order o WHERE o.id=?
          """,
          UUID.randomUUID(),
          tenant,
          variant,
          quantity,
          id);
    return id;
  }

  private String status(UUID order) {
    return jdbc.queryForObject("SELECT status FROM sales_order WHERE id=?", String.class, order);
  }

  private String reservationStatus(UUID order) {
    return jdbc.queryForObject(
        "SELECT status FROM inventory_reservation WHERE order_id=?", String.class, order);
  }

  private int available(UUID tenant, UUID variant) {
    Integer value =
        jdbc.queryForObject(
            "SELECT inventory_available_quantity(?,?,now())", Integer.class, tenant, variant);
    return value == null ? -1 : value;
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? -1 : value;
  }

  private String publicStatus(UUID order) throws Exception {
    String token = "T".repeat(43);
    jdbc.update(
        "UPDATE sales_order SET access_token_hash=encode(sha256(convert_to(?,'UTF8')),'hex')"
            + " WHERE id=?",
        token,
        order);
    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id=?", tenantA);
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create(
                            "http://127.0.0.1:"
                                + port
                                + "/api/public/stores/pedidos-a/orders/"
                                + order))
                    .header("X-Order-Token", token)
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body()).get("status").asString();
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/orders";
  }

  private static String cancel(UUID tenant, UUID order) {
    return path(tenant) + "/" + order + "/cancel";
  }

  private UUID identity(String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, ?, ?, 'ACTIVE', now(), now(), now())",
        id,
        email,
        email);
    jdbc.update(
        "INSERT INTO identity_local_credential (user_id, password_hash, created_at, updated_at) VALUES (?, ?, now(), now())",
        id,
        HASH);
    return id;
  }

  private UUID store(UUID owner, String slug) {
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'OWNER', now())",
        tenant,
        owner);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, created_at) VALUES (?, ?, 'Synthetic store', ?, now())",
        UUID.randomUUID(),
        tenant,
        slug);
    return tenant;
  }

  private UUID variant(UUID tenant, String sku, int stock) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind) SELECT ?,?,id,'Produto',?,10.00,'BRL',now(),now(),true,'SIMPLE' FROM store WHERE tenant_id=?",
        id,
        tenant,
        sku,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,10.00,4.00,'BRL','[]',?)",
        id,
        tenant,
        id,
        sku,
        id.toString());
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,?)",
        id,
        tenant,
        stock);
    return id;
  }

  private Browser login(String email) throws Exception {
    Browser browser = new Browser();
    browser.csrf();
    assertThat(
            browser
                .post("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    return browser;
  }

  private final class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asString();
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null);
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
      return send("POST", path, JSON.writeValueAsString(body), csrfToken);
    }

    HttpResponse<String> send(String method, String path, String body, String csrf)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(20));
      if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
      if (body != null) request.header("Content-Type", "application/json");
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(body));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
