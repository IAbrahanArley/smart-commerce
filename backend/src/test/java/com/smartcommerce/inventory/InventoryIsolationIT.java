package com.smartcommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.smartcommerce.inventory.domain.ReservationLine;
import com.smartcommerce.inventory.services.InventoryReservationService;
import com.smartcommerce.orders.TestOrders;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Session, CSRF, monetary contracts and tenant isolation against a disposable PostgreSQL. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryIsolationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic inventory integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired InventoryReservationService reservations;
  UUID ownerA;
  UUID ownerB;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = activeIdentity("catalog-a@example.test");
    ownerB = activeIdentity("catalog-b@example.test");
    tenantA = store(ownerA, "catalog-a");
    tenantB = store(ownerB, "catalog-b");
  }

  @Test
  void recordsEntryAdjustmentAuditAndIdempotentReplayWithoutDuplicatingStock() throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = createProduct(a, tenantA, "ONE");
    String route = inventory(tenantA, product);
    assertThat(JSON.readTree(a.get(route).body()).get("quantity").asInt()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_balance WHERE product_id=?", Long.class, product))
        .isZero();
    Map<String, Object> entry = movement("ENTRY", 10, "Saldo inicial");
    entry.put("actorId", ownerB.toString());
    entry.put("tenantId", tenantB.toString());
    var first = a.post(route, entry);
    assertThat(first.statusCode()).isEqualTo(201);
    JsonNode original = JSON.readTree(first.body()).get("movement");
    assertThat(original.get("balanceAfter").asInt()).isEqualTo(10);
    assertThat(a.post(route, movement("ADJUSTMENT", -3, "Contagem física")).statusCode())
        .isEqualTo(201);
    var replay = a.post(route, entry);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(replay.body()).get("replayed").asBoolean()).isTrue();
    assertThat(JSON.readTree(replay.body()).get("movement")).isEqualTo(original);
    JsonNode state = JSON.readTree(a.get(route).body());
    assertThat(state.get("quantity").asInt()).isEqualTo(7);
    assertThat(state.get("movements").size()).isEqualTo(2);
    assertThat(state.get("movements").get(0).get("delta").asInt()).isEqualTo(-3);
    assertThat(
            jdbc.queryForObject(
                "SELECT actor_id FROM inventory_movement WHERE id=?",
                UUID.class,
                UUID.fromString(original.get("id").asText())))
        .isEqualTo(ownerA);
  }

  @Test
  void rejectsInvalidQuantitiesReasonsAndConflictingRequestIdsAtomically() throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = createProduct(a, tenantA, "ONE");
    String route = inventory(tenantA, product);
    for (Object invalid : List.of("2", 2.5, true, 2147483648L, 0, -1)) {
      assertThat(a.post(route, movement("ENTRY", invalid, "Entrada")).statusCode()).isEqualTo(400);
    }
    for (String reason : new String[] {"", "   ", "a".repeat(241), "line\nbreak", "\u200Bhidden"}) {
      assertThat(a.post(route, movement("ENTRY", 1, reason)).statusCode()).isEqualTo(400);
    }
    assertThat(a.post(route, movement("ADJUSTMENT", 0, "Ajuste")).statusCode()).isEqualTo(400);
    Map<String, Object> request = movement("ENTRY", 5, "Entrada");
    assertThat(a.post(route, request).statusCode()).isEqualTo(201);
    request.put("delta", 6);
    var conflict = a.post(route, request);
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(conflict.body()).get("code").asText()).isEqualTo("REQUEST_CONFLICT");
    UUID another = createProduct(a, tenantA, "TWO");
    assertThat(a.post(inventory(tenantA, another), request).statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(a.get(inventory(tenantA, another)).body()).get("quantity").asInt())
        .isZero();
    assertThat(a.post(route, movement("ENTRY", 2147483647, "Limite")).statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(a.get(route).body()).get("quantity").asInt()).isEqualTo(5);
    assertThat(JSON.readTree(a.get(route).body()).get("movements").size()).isEqualTo(1);
  }

  @Test
  void deniesForeignProductsForgedTenantsRevokedMembershipSessionAndCsrf() throws Exception {
    Browser a = login("catalog-a@example.test");
    Browser b = login("catalog-b@example.test");
    UUID product = createProduct(a, tenantA, "OWN");
    String route = inventory(tenantA, product);
    for (String forbidden :
        List.of(route, inventory(tenantB, product), inventory(tenantB, UUID.randomUUID()))) {
      assertThat(b.get(forbidden).statusCode()).isEqualTo(404);
      assertThat(b.post(forbidden, movement("ENTRY", 1, "Teste")).statusCode()).isEqualTo(404);
    }
    Browser anonymous = new Browser();
    assertThat(anonymous.get(route).statusCode()).isEqualTo(401);
    assertThat(
            a.send(
                    "POST",
                    route,
                    JSON.writeValueAsString(movement("ENTRY", 1, "Teste")),
                    null,
                    null)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            a.send(
                    "POST",
                    route,
                    JSON.writeValueAsString(movement("ENTRY", 1, "Teste")),
                    a.csrfToken,
                    "https://foreign.example")
                .statusCode())
        .isEqualTo(403);
    jdbc.update("DELETE FROM tenant_membership WHERE tenant_id=? AND user_id=?", tenantA, ownerA);
    assertThat(a.get(route).statusCode()).isEqualTo(404);
    assertThat(a.post(route, movement("ENTRY", 1, "Teste")).statusCode()).isEqualTo(404);
  }

  @Test
  void concurrentRequestsAreIdempotentAndWithdrawalsCannotMakeBalanceNegative() throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = createProduct(a, tenantA, "RACE");
    String route = inventory(tenantA, product);
    var initial = movement("ENTRY", 5, "Entrada concorrente");
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                barrier.await();
                return a.post(route, initial).statusCode();
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                return a.post(route, initial).statusCode();
              });
      assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 200);
      var outOne =
          pool.submit(
              () -> {
                barrier.await();
                return a.post(route, movement("ADJUSTMENT", -4, "Saída um")).statusCode();
              });
      var outTwo =
          pool.submit(
              () -> {
                barrier.await();
                return a.post(route, movement("ADJUSTMENT", -4, "Saída dois")).statusCode();
              });
      assertThat(List.of(outOne.get(20, TimeUnit.SECONDS), outTwo.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    var state = JSON.readTree(a.get(route).body());
    assertThat(state.get("quantity").asInt()).isEqualTo(1);
    assertThat(state.get("movements").size()).isEqualTo(2);
  }

  @Test
  void negativeAdjustmentBelowReservedReturnsReservedStockAndAdminShowsAllLevels()
      throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = createProduct(a, tenantA, "RESERVED");
    String route = inventory(tenantA, product);
    assertThat(a.post(route, movement("ENTRY", 5, "Entrada")).statusCode()).isEqualTo(201);
    UUID order = TestOrders.create(jdbc, tenantA);
    reservations.reserve(tenantA, order, List.of(new ReservationLine(product, 3)));
    JsonNode state = JSON.readTree(a.get(route).body());
    assertThat(state.get("quantity").asInt()).isEqualTo(5);
    assertThat(state.get("reserved").asLong()).isEqualTo(3);
    assertThat(state.get("available").asInt()).isEqualTo(2);
    var blocked = a.post(route, movement("ADJUSTMENT", -3, "Contagem física"));
    assertThat(blocked.statusCode()).isEqualTo(409);
    JsonNode error = JSON.readTree(blocked.body());
    assertThat(error.get("code").asText()).isEqualTo("RESERVED_STOCK");
    assertThat(error.get("reserved").asLong()).isEqualTo(3);
    var insufficient = a.post(route, movement("ADJUSTMENT", -6, "Contagem física"));
    assertThat(JSON.readTree(insufficient.body()).get("code").asText())
        .isEqualTo("INSUFFICIENT_STOCK");
    assertThat(a.post(route, movement("ADJUSTMENT", -2, "Contagem física")).statusCode())
        .isEqualTo(201);
    assertThat(a.post(route, movement("ADJUSTMENT", 1, "Ajuste positivo")).statusCode())
        .isEqualTo(201);
    Browser b = login("catalog-b@example.test");
    assertThat(b.get(inventory(tenantB, product)).statusCode()).isEqualTo(404);
    assertThat(b.get(route).body()).doesNotContain("reserved");
    assertThat(reservations.release(tenantA, order)).isEqualTo(1);
    state = JSON.readTree(a.get(route).body());
    assertThat(state.get("quantity").asInt()).isEqualTo(4);
    assertThat(state.get("reserved").asLong()).isZero();
    assertThat(state.get("available").asInt()).isEqualTo(4);
    assertThat(state.get("movements").size()).isEqualTo(3);
  }

  @Test
  void historyCursorDoesNotRepeatMovementsAndSchemaRejectsCrossTenantBalances() throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = createProduct(a, tenantA, "PAGE");
    String route = inventory(tenantA, product);
    for (int i = 0; i < 21; i++)
      assertThat(a.post(route, movement("ENTRY", 1, "Entrada " + i)).statusCode()).isEqualTo(201);
    JsonNode first = JSON.readTree(a.get(route).body());
    assertThat(first.get("quantity").asInt()).isEqualTo(21);
    assertThat(first.get("movements").size()).isEqualTo(20);
    String cursor = first.get("nextCursor").asText();
    JsonNode next = JSON.readTree(a.get(route + "?before=" + cursor).body());
    assertThat(next.get("movements").size()).isEqualTo(1);
    assertThat(next.get("nextCursor").isNull()).isTrue();
    assertThat(next.get("movements").get(0).get("id").asText())
        .isNotEqualTo(first.get("movements").get(19).get("id").asText());
    assertThat(a.get(route + "?before=9223372036854775808").statusCode()).isEqualTo(400);
    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE inventory_balance SET quantity=-1 WHERE product_id=?", product))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE inventory_balance SET tenant_id=? WHERE product_id=?",
                    tenantB,
                    product))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(JSON.readTree(a.get(route).body()).get("quantity").asInt()).isEqualTo(21);
  }

  private UUID createProduct(Browser browser, UUID tenant, String sku) throws Exception {
    var response = browser.post(path(tenant), product("Produto de estoque", sku, "10.00", null));
    assertThat(response.statusCode()).isEqualTo(201);
    return UUID.fromString(JSON.readTree(response.body()).get("id").asText());
  }

  private static String inventory(UUID tenant, UUID product) {
    return path(tenant) + "/" + product + "/inventory";
  }

  private static Map<String, Object> movement(String type, Object delta, String reason) {
    var data = new HashMap<String, Object>();
    data.put("requestId", UUID.randomUUID().toString());
    data.put("type", type);
    data.put("delta", delta);
    data.put("reason", reason);
    return data;
  }

  private UUID activeIdentity(String email) {
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

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/products";
  }

  private static Map<String, Object> product(String name, String sku, Object price, Object cost) {
    Map<String, Object> result = new HashMap<>();
    result.put("name", name);
    result.put("sku", sku);
    result.put("price", price);
    result.put("cost", cost);
    return result;
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
      csrfToken = JSON.readTree(response.body()).get("token").asText();
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null, null);
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
      return send("POST", path, JSON.writeValueAsString(body), csrfToken, null);
    }

    HttpResponse<String> put(String path, Object body) throws Exception {
      return send("PUT", path, JSON.writeValueAsString(body), csrfToken, null);
    }

    HttpResponse<String> send(String method, String path, String body, String csrf, String origin)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(20));
      if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
      if (origin != null) request.header("Origin", origin);
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
