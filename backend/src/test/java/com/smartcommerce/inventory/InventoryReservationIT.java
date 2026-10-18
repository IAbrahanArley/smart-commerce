package com.smartcommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.inventory.domain.InventoryException;
import com.smartcommerce.inventory.domain.InventoryInput;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.repositories.InventoryRepository;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.inventory.services.InventoryService;
import com.smartcommerce.orders.TestOrders;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** SC-005: reservations, availability, D7 and tenant isolation against a disposable PostgreSQL. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryReservationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired InventoryReservationService reservations;
  @Autowired InventoryService inventoryService;
  @Autowired InventoryRepository inventory;
  UUID ownerA;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, 'res-a@example.test', 'res-a@example.test', 'ACTIVE', now(), now(), now())",
        ownerA);
    tenantA = store("reserva-a", ownerA);
    tenantB = store("reserva-b", null);
  }

  @Test
  void availabilityExcludesReservationsBeforeButNotExactlyAtOrAfterTheDeadline() {
    UUID variant = product(tenantA, "EDGE", 5);
    Instant expiresAt = Instant.parse("2026-10-17T12:30:00.000001Z");
    jdbc.update(
        "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at) VALUES (?,?,?,?,2,'ACTIVE',?,?)",
        UUID.randomUUID(),
        tenantA,
        variant,
        TestOrders.create(jdbc, tenantA),
        Timestamp.from(expiresAt),
        Timestamp.from(expiresAt.minus(Duration.ofMinutes(30))));
    var before = inventory.level(tenantA, variant, expiresAt.minus(1, ChronoUnit.MICROS));
    assertThat(List.of(before.onHand(), before.reserved(), (long) before.available()))
        .containsExactly(5, 2L, 3L);
    for (Instant at : List.of(expiresAt, expiresAt.plus(1, ChronoUnit.MICROS))) {
      var level = inventory.level(tenantA, variant, at);
      assertThat(level.reserved()).isZero();
      assertThat(level.available()).isEqualTo(5);
    }
    assertThat(inventory.level(tenantA, product(tenantA, "NOBALANCE", -1), expiresAt).available())
        .isZero();
  }

  @Test
  void storefrontAndAdminReflectReservationsAndRecoverAfterDeadlineWithoutRoutine()
      throws Exception {
    UUID variant = product(tenantA, "LAST", 2);
    long movements = movements();
    var result =
        reservations.reserve(tenantA, TestOrders.create(jdbc, tenantA), List.of(line(variant, 2)));
    assertThat(result.expiresAt())
        .isBetween(Instant.now().plus(Duration.ofMinutes(29)), Instant.now().plusSeconds(1800));
    assertThat(publicAvailable(variant)).isFalse();
    var admin = inventoryService.find(ownerA, tenantA, variant, null);
    assertThat(List.of(admin.quantity(), admin.reserved(), (long) admin.available()))
        .containsExactly(2, 2L, 0L);
    jdbc.update(
        "UPDATE inventory_reservation SET created_at=now()-interval '1 hour', expires_at=now()-interval '1 second' WHERE variant_id=?",
        variant);
    assertThat(publicAvailable(variant)).isTrue();
    assertThat(inventoryService.find(ownerA, tenantA, variant, null).available()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inventory_reservation WHERE variant_id=?",
                String.class,
                variant))
        .isEqualTo("ACTIVE");
    assertThat(reservations.expireDue(100)).isEqualTo(1);
    assertThat(reservations.expireDue(100)).isZero();
    assertThat(movements()).isEqualTo(movements);
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM inventory_balance WHERE product_id=?",
                Integer.class,
                variant))
        .isEqualTo(2);
  }

  @Test
  void releaseIsIdempotentAndRestoresAvailabilityImmediately() throws Exception {
    UUID variant = product(tenantA, "REL", 3);
    UUID order = TestOrders.create(jdbc, tenantA);
    reservations.reserve(tenantA, order, List.of(line(variant, 3)));
    assertThat(publicAvailable(variant)).isFalse();
    assertThatThrownBy(
            () ->
                reservations.reserve(
                    tenantA, TestOrders.create(jdbc, tenantA), List.of(line(variant, 1))))
        .hasFieldOrPropertyWithValue("reason", InventoryException.Reason.INSUFFICIENT_STOCK);
    assertThat(reservations.release(tenantB, order)).isZero();
    assertThat(reservations.release(tenantA, order)).isEqualTo(1);
    assertThat(reservations.release(tenantA, order)).isZero();
    assertThat(publicAvailable(variant)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inventory_reservation WHERE order_id=?", String.class, order))
        .isEqualTo("RELEASED");
    assertThatThrownBy(() -> reservations.reserve(tenantA, order, List.of(line(variant, 1))))
        .hasFieldOrPropertyWithValue("reason", InventoryException.Reason.REQUEST_CONFLICT);
  }

  @Test
  void exactlyOneOfManyConcurrentOrdersReservesTheLastUnit() throws Exception {
    UUID variant = product(tenantA, "RACE", 1);
    int threads = 12;
    var barrier = new CyclicBarrier(threads);
    var tasks = new ArrayList<Callable<String>>();
    for (int i = 0; i < threads; i++)
      tasks.add(
          () -> {
            barrier.await(20, TimeUnit.SECONDS);
            try {
              reservations.reserve(
                  tenantA, TestOrders.create(jdbc, tenantA), List.of(line(variant, 1)));
              return "OK";
            } catch (InventoryException exception) {
              return exception.reason().name();
            }
          });
    List<String> outcomes = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(threads)) {
      for (var future : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) outcomes.add(future.get());
    }
    assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
    assertThat(outcomes).filteredOn("INSUFFICIENT_STOCK"::equals).hasSize(threads - 1);
    var level = inventory.level(tenantA, variant, Instant.now());
    assertThat(level.reserved()).isEqualTo(1);
    assertThat(level.available()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_reservation WHERE variant_id=?",
                Long.class,
                variant))
        .isEqualTo(1);
  }

  @Test
  void crossedVariantOrdersLockInVariantOrderWithoutDeadlock() throws Exception {
    UUID x = product(tenantA, "CROSS-X", 1000);
    UUID y = product(tenantA, "CROSS-Y", 1000);
    int rounds = 25;
    var barrier = new CyclicBarrier(2);
    List<String> failures = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(2)) {
      for (int round = 0; round < rounds; round++) {
        var one =
            pool.submit(
                () -> {
                  barrier.await(20, TimeUnit.SECONDS);
                  return reservations.reserve(
                      tenantA, TestOrders.create(jdbc, tenantA), List.of(line(x, 1), line(y, 1)));
                });
        var two =
            pool.submit(
                () -> {
                  barrier.await(20, TimeUnit.SECONDS);
                  return reservations.reserve(
                      tenantA, TestOrders.create(jdbc, tenantA), List.of(line(y, 1), line(x, 1)));
                });
        for (var future : List.of(one, two)) {
          try {
            future.get(30, TimeUnit.SECONDS);
          } catch (Exception exception) {
            failures.add(String.valueOf(exception.getCause()));
          }
        }
      }
    }
    assertThat(failures).isEmpty();
    assertThat(inventory.level(tenantA, x, Instant.now()).reserved()).isEqualTo(2L * rounds);
    assertThat(inventory.level(tenantA, y, Instant.now()).reserved()).isEqualTo(2L * rounds);
  }

  @Test
  void negativeAdjustmentAndConcurrentReservationNeverLeavePhysicalBelowReserved()
      throws Exception {
    for (int round = 0; round < 10; round++) {
      UUID variant = product(tenantA, "D7-" + round, 5);
      var barrier = new CyclicBarrier(2);
      try (var pool = Executors.newFixedThreadPool(2)) {
        var reserve =
            pool.submit(
                () -> {
                  barrier.await(20, TimeUnit.SECONDS);
                  try {
                    reservations.reserve(
                        tenantA, TestOrders.create(jdbc, tenantA), List.of(line(variant, 3)));
                    return "OK";
                  } catch (InventoryException exception) {
                    return exception.reason().name();
                  }
                });
        var adjust =
            pool.submit(
                () -> {
                  barrier.await(20, TimeUnit.SECONDS);
                  try {
                    inventoryService.move(
                        ownerA,
                        tenantA,
                        variant,
                        new InventoryInput(
                            UUID.randomUUID(),
                            InventoryInput.MovementType.ADJUSTMENT,
                            -3,
                            "Contagem física"));
                    return "OK";
                  } catch (InventoryException exception) {
                    return exception.reason().name();
                  }
                });
        var outcome = List.of(reserve.get(30, TimeUnit.SECONDS), adjust.get(30, TimeUnit.SECONDS));
        assertThat(outcome)
            .isIn(List.of("OK", "RESERVED_STOCK"), List.of("INSUFFICIENT_STOCK", "OK"));
      }
      var level = inventory.level(tenantA, variant, Instant.now());
      assertThat((long) level.onHand()).isGreaterThanOrEqualTo(level.reserved());
      assertThat(level.available()).isEqualTo(2);
    }
  }

  @Test
  void reservationsAreIsolatedByTenantEvenWithSameSkuAndViaDirectSql() throws Exception {
    UUID variantA = product(tenantA, "SAME-SKU", 1);
    UUID variantB = product(tenantB, "SAME-SKU", 1);
    reservations.reserve(tenantA, TestOrders.create(jdbc, tenantA), List.of(line(variantA, 1)));
    var levelB = inventory.level(tenantB, variantB, Instant.now());
    assertThat(levelB.reserved()).isZero();
    assertThat(levelB.available()).isEqualTo(1);
    assertThat(publicAvailable("reserva-b", variantB)).isTrue();
    UUID foreignOrder = UUID.randomUUID();
    assertThatThrownBy(
            () -> reservations.reserve(tenantA, foreignOrder, List.of(line(variantB, 1))))
        .hasFieldOrPropertyWithValue("reason", InventoryException.Reason.MISSING);
    assertThatThrownBy(
            () ->
                reservations.reserve(
                    tenantA, foreignOrder, List.of(line(variantA, 1), line(variantB, 1))))
        .hasFieldOrPropertyWithValue("reason", InventoryException.Reason.MISSING);
    assertThat(inventory.levels(tenantA, List.of(variantB), Instant.now())).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_reservation WHERE order_id=?",
                Long.class,
                foreignOrder))
        .isZero();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at) VALUES (?,?,?,?,1,'ACTIVE',now()+interval '30 minutes',now())",
                    UUID.randomUUID(),
                    tenantA,
                    variantB,
                    TestOrders.create(jdbc, tenantA)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE inventory_reservation SET tenant_id=? WHERE variant_id=?",
                    tenantB,
                    variantA))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void schemaRejectsInvalidQuantitiesStatesWindowsAndDuplicatedOrderLines() {
    UUID variant = product(tenantA, "CHECKS", 5);
    UUID order = TestOrders.create(jdbc, tenantA);
    String insert =
        "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at,closed_at) VALUES (?,?,?,?,?,?,now()+(?)::interval,now(),?)";
    for (Object[] invalid :
        List.<Object[]>of(
            new Object[] {0, "ACTIVE", "30 minutes", null},
            new Object[] {-1, "ACTIVE", "30 minutes", null},
            new Object[] {1, "HELD", "30 minutes", null},
            new Object[] {1, "ACTIVE", "-1 second", null},
            new Object[] {1, "ACTIVE", "30 minutes", Timestamp.from(Instant.now())},
            new Object[] {1, "RELEASED", "30 minutes", null}))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      insert,
                      UUID.randomUUID(),
                      tenantA,
                      variant,
                      order,
                      invalid[0],
                      invalid[1],
                      invalid[2],
                      invalid[3]))
          .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        insert, UUID.randomUUID(), tenantA, variant, order, 1, "ACTIVE", "30 minutes", null);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    UUID.randomUUID(),
                    tenantA,
                    variant,
                    order,
                    1,
                    "ACTIVE",
                    "30 minutes",
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static ReservationLine line(UUID variant, int quantity) {
    return new ReservationLine(variant, quantity);
  }

  private long movements() {
    return jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Long.class);
  }

  private boolean publicAvailable(UUID variant) throws Exception {
    return publicAvailable("reserva-a", variant);
  }

  /** Simple products share the variant id; checks both the product and the variant flags. */
  private boolean publicAvailable(String slug, UUID variant) throws Exception {
    var response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://127.0.0.1:"
                            + port
                            + "/api/public/stores/"
                            + slug
                            + "/products/"
                            + variant))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode product = JSON.readTree(response.body()).get("product");
    boolean available = product.get("available").asBoolean();
    assertThat(product.get("variants").get(0).get("available").asBoolean()).isEqualTo(available);
    assertThat(response.body()).doesNotContain("quantity").doesNotContain("reserved");
    var list =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/public/stores/" + slug))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    for (JsonNode item : JSON.readTree(list.body()).get("products"))
      if (item.get("id").asText().equals(variant.toString()))
        assertThat(item.get("available").asBoolean()).isEqualTo(available);
    return available;
  }

  private UUID store(String slug, UUID owner) {
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    if (owner != null)
      jdbc.update(
          "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'OWNER', now())",
          tenant,
          owner);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, status, created_at) VALUES (?, ?, 'Synthetic store', ?, 'PUBLISHED', now())",
        UUID.randomUUID(),
        tenant,
        slug);
    return tenant;
  }

  /** Published simple product; its sellable variant shares the product id. -1 = no balance row. */
  private UUID product(UUID tenant, String sku, int quantity) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,cost,currency,created_at,updated_at,published) SELECT ?,?,id,'Synthetic item',?,10.00,1.00,'BRL',now(),now(),true FROM store WHERE tenant_id=?",
        id,
        tenant,
        sku,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,10.00,1.00,'BRL','[]','')",
        id,
        tenant,
        id,
        sku);
    if (quantity >= 0)
      jdbc.update(
          "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES (?,?,?)",
          id,
          tenant,
          quantity);
    return id;
  }
}
