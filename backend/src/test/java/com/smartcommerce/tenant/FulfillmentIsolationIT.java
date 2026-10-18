package com.smartcommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** SC-006: owner configuration, optimistic versions and public exposure across two tenants. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FulfillmentIsolationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic fulfillment integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID ownerA;
  UUID ownerB;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = activeIdentity("fulfillment-a@example.test");
    ownerB = activeIdentity("fulfillment-b@example.test");
    tenantA = store(ownerA, "entrega-a");
    tenantB = store(ownerB, "entrega-b");
  }

  @Test
  void ownerSavesReloadsAndKeepsExactValuesWithOptimisticVersions() throws Exception {
    Browser a = login("fulfillment-a@example.test");
    var initial = a.get(path(tenantA));
    assertThat(initial.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(initial.body()))
        .isEqualTo(
            JSON.readTree(
                "{\"pickupEnabled\":false,\"pickupInstructions\":null,\"fixedShippingEnabled\":false,"
                    + "\"fixedShippingAmount\":null,\"currency\":\"BRL\",\"fixedShippingArea\":null,\"version\":0}"));
    var saved = a.put(path(tenantA), settings(true, "Rua A, 10", true, "0", "Centro", 0));
    assertThat(saved.statusCode()).isEqualTo(200);
    JsonNode body = JSON.readTree(saved.body());
    assertThat(body.get("fixedShippingAmount").asText()).isEqualTo("0.00");
    assertThat(body.get("version").asLong()).isEqualTo(1);
    assertThat(JSON.readTree(a.get(path(tenantA)).body())).isEqualTo(body);

    var stale = a.put(path(tenantA), settings(false, null, true, "9.90", "Centro", 0));
    assertThat(stale.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(stale.body()).get("code").asText()).isEqualTo("VERSION_CONFLICT");
    var future = a.put(path(tenantA), settings(false, null, true, "9.90", "Centro", 7));
    assertThat(future.statusCode()).isEqualTo(409);

    var updated = a.put(path(tenantA), settings(false, "Rua A, 10", true, "12.5", " Bairro ", 1));
    assertThat(updated.statusCode()).isEqualTo(200);
    JsonNode reloaded = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(reloaded.get("pickupEnabled").asBoolean()).isFalse();
    assertThat(reloaded.get("pickupInstructions").asText()).isEqualTo("Rua A, 10");
    assertThat(reloaded.get("fixedShippingAmount").asText()).isEqualTo("12.50");
    assertThat(reloaded.get("fixedShippingArea").asText()).isEqualTo("Bairro");
    assertThat(reloaded.get("version").asLong()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT fixed_shipping_amount::text FROM store_fulfillment WHERE tenant_id=?",
                String.class,
                tenantA))
        .isEqualTo("12.50");
  }

  @Test
  void invalidValuesAreRejectedWithoutChangingStoredConfiguration() throws Exception {
    Browser a = login("fulfillment-a@example.test");
    assertThat(
            a.put(path(tenantA), settings(true, "Rua A", true, "5.00", "Centro", 0)).statusCode())
        .isEqualTo(200);
    String text501 = "x".repeat(501);
    List<Map<String, Object>> invalid =
        List.of(
            settings(false, null, true, "-1", "Centro", 1),
            settings(false, null, true, "-0.01", "Centro", 1),
            settings(false, null, true, "1.999", "Centro", 1),
            settings(false, null, true, "1,50", "Centro", 1),
            settings(true, "Rua\nA", false, null, null, 1),
            settings(true, "Rua\u0007A", false, null, null, 1),
            settings(false, null, true, "1", "Centro\u202e", 1),
            settings(true, text501, false, null, null, 1),
            settings(false, null, true, "1", text501, 1),
            settings(true, "   ", false, null, null, 1),
            settings(false, null, true, null, "Centro", 1),
            settings(false, null, true, "1", null, 1));
    for (var request : invalid)
      assertThat(a.put(path(tenantA), request).statusCode()).as(request.toString()).isEqualTo(400);
    var numeric = settings(false, null, true, "1", "Centro", 1);
    numeric.put("fixedShippingAmount", 1.5);
    assertThat(a.put(path(tenantA), numeric).statusCode()).isEqualTo(400);
    var textual = settings(false, null, true, "1", "Centro", 1);
    textual.put("pickupEnabled", "true");
    assertThat(a.put(path(tenantA), textual).statusCode()).isEqualTo(400);
    var missingVersion = settings(false, null, true, "1", "Centro", 1);
    missingVersion.remove("version");
    assertThat(a.put(path(tenantA), missingVersion).statusCode()).isEqualTo(400);
    var negativeVersion = settings(false, null, true, "1", "Centro", -1);
    assertThat(a.put(path(tenantA), negativeVersion).statusCode()).isEqualTo(400);
    assertThat(a.send("PUT", path(tenantA), "{not json", a.csrfToken, null).statusCode())
        .isEqualTo(400);
    JsonNode stored = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(stored.get("version").asLong()).isEqualTo(1);
    assertThat(stored.get("fixedShippingAmount").asText()).isEqualTo("5.00");
  }

  @Test
  void schemaRejectsInvalidMoneyAndCrossTenantRowsEvenThroughDirectSql() {
    UUID storeA =
        jdbc.queryForObject("SELECT id FROM store WHERE tenant_id=?", UUID.class, tenantA);
    String insert =
        "INSERT INTO store_fulfillment(store_id,tenant_id,fixed_shipping_enabled,fixed_shipping_amount,fixed_shipping_area,updated_at) VALUES(?,?,true,?::numeric,'Centro',now())";
    assertThatThrownBy(() -> jdbc.update(insert, storeA, tenantB, "1.00"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(insert, storeA, tenantA, "-0.01"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(insert, storeA, tenantA, "1.001"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO store_fulfillment(store_id,tenant_id,pickup_enabled,updated_at) VALUES(?,?,true,now())",
                    storeA,
                    tenantA))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.update(insert, storeA, tenantA, "0")).isEqualTo(1);
  }

  @Test
  void ownersCannotReadOrChangeAnotherTenantAndMutationsRequireCsrfAndOrigin() throws Exception {
    Browser a = login("fulfillment-a@example.test");
    Browser b = login("fulfillment-b@example.test");
    assertThat(
            a.put(path(tenantA), settings(true, "Segredo A", true, "7.00", "Área A", 0))
                .statusCode())
        .isEqualTo(200);
    var cross = b.get(path(tenantA));
    var absent = b.get(path(UUID.randomUUID()));
    assertThat(cross.statusCode()).isEqualTo(404);
    assertThat(absent.statusCode()).isEqualTo(404);
    assertThat(cross.body()).isEqualTo(absent.body()).doesNotContain("Segredo A");
    var crossWrite = b.put(path(tenantA), settings(true, "Invasor", false, null, null, 1));
    var absentWrite =
        b.put(path(UUID.randomUUID()), settings(true, "Invasor", false, null, null, 1));
    assertThat(crossWrite.statusCode()).isEqualTo(404);
    assertThat(crossWrite.body()).isEqualTo(absentWrite.body());
    assertThat(b.put(path(tenantA), settings(true, "Invasor", false, null, null, 0)).statusCode())
        .isEqualTo(404);
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("pickupInstructions").asText())
        .isEqualTo("Segredo A");
    assertThat(JSON.readTree(b.get(path(tenantB)).body()).get("version").asLong()).isZero();

    String payload = JSON.writeValueAsString(settings(true, "Rua A", false, null, null, 1));
    assertThat(a.send("PUT", path(tenantA), payload, null, null).statusCode()).isEqualTo(403);
    assertThat(a.send("PUT", path(tenantA), payload, "incorrect", null).statusCode())
        .isEqualTo(403);
    assertThat(
            a.send("PUT", path(tenantA), payload, a.csrfToken, "https://attacker.example")
                .statusCode())
        .isEqualTo(403);
    Browser anonymous = new Browser();
    assertThat(anonymous.get(path(tenantA)).statusCode()).isEqualTo(401);
    assertThat(a.get("/api/tenants/not-a-uuid/fulfillment").statusCode()).isEqualTo(400);

    jdbc.update("UPDATE identity_user SET status='DISABLED' WHERE id=?", ownerA);
    assertThat(a.get(path(tenantA)).statusCode()).isIn(401, 404);
    assertThat(a.put(path(tenantA), settings(true, "Rua A", false, null, null, 1)).statusCode())
        .isIn(401, 403, 404);
    jdbc.update("UPDATE identity_user SET status='ACTIVE' WHERE id=?", ownerA);
    a = login("fulfillment-a@example.test");
    jdbc.update("DELETE FROM tenant_membership WHERE tenant_id=? AND user_id=?", tenantA, ownerA);
    assertThat(a.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(a.put(path(tenantA), settings(true, "Rua A", false, null, null, 1)).statusCode())
        .isEqualTo(404);
  }

  @Test
  void concurrentSavesWithTheSameVersionHaveExactlyOneWinner() throws Exception {
    Browser a = login("fulfillment-a@example.test");
    int writers = 6;
    var barrier = new CyclicBarrier(writers);
    try (var pool = Executors.newFixedThreadPool(writers)) {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < writers; i++) {
        String amount = i + ".00";
        Callable<Integer> task =
            () -> {
              barrier.await();
              return a.put(path(tenantA), settings(false, null, true, amount, "Centro", 0))
                  .statusCode();
            };
        results.add(pool.submit(task));
      }
      List<Integer> statuses = new ArrayList<>();
      for (var result : results) statuses.add(result.get());
      assertThat(statuses).containsOnly(200, 409);
      assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
    }
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("version").asLong()).isEqualTo(1);
  }

  @Test
  void publicEndpointShowsOnlyEnabledOptionsOfPublishedStoreWithoutMixingTenants()
      throws Exception {
    Browser a = login("fulfillment-a@example.test");
    Browser b = login("fulfillment-b@example.test");
    Browser visitor = new Browser();
    String publicA = "/api/public/stores/entrega-a/fulfillment";
    String publicB = "/api/public/stores/entrega-b/fulfillment";
    assertThat(
            a.put(path(tenantA), settings(true, "Retirada A", true, "0", "Área A", 0)).statusCode())
        .isEqualTo(200);
    assertThat(
            b.put(path(tenantB), settings(false, "Retirada B oculta", true, "15.90", "Área B", 0))
                .statusCode())
        .isEqualTo(200);

    var draft = visitor.get(publicA);
    var unknown = visitor.get("/api/public/stores/nao-existe/fulfillment");
    assertThat(draft.statusCode()).isEqualTo(404);
    assertThat(draft.body()).isEqualTo(unknown.body()).doesNotContain("Retirada A");
    assertThat(visitor.get("/api/public/stores/INVALID_SLUG/fulfillment").statusCode())
        .isEqualTo(404);

    publish(a, tenantA);
    publish(b, tenantB);
    var shownA = visitor.get(publicA);
    assertThat(shownA.statusCode()).isEqualTo(200);
    assertThat(shownA.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(shownA.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(JSON.readTree(shownA.body()))
        .isEqualTo(
            JSON.readTree(
                "{\"options\":[{\"type\":\"PICKUP\",\"instructions\":\"Retirada A\"},"
                    + "{\"type\":\"FIXED_SHIPPING\",\"amount\":\"0.00\",\"currency\":\"BRL\",\"area\":\"Área A\"}]}"));
    var shownB = visitor.get(publicB);
    assertThat(JSON.readTree(shownB.body()))
        .isEqualTo(
            JSON.readTree(
                "{\"options\":[{\"type\":\"FIXED_SHIPPING\",\"amount\":\"15.90\",\"currency\":\"BRL\",\"area\":\"Área B\"}]}"));
    assertThat(shownB.body())
        .doesNotContain("Retirada B oculta", "Retirada A", "Área A", "version");

    jdbc.update("DELETE FROM store_fulfillment WHERE tenant_id=?", tenantB);
    assertThat(JSON.readTree(visitor.get(publicB).body()))
        .isEqualTo(JSON.readTree("{\"options\":[]}"));
    assertThat(visitor.send("PUT", publicA, "{}", null, null).statusCode()).isEqualTo(403);

    assertThat(
            a.put("/api/tenants/" + tenantA + "/publication", Map.of("published", false))
                .statusCode())
        .isEqualTo(200);
    assertThat(visitor.get(publicA).statusCode()).isEqualTo(404);
  }

  private void publish(Browser owner, UUID tenant) throws Exception {
    assertThat(
            owner
                .put("/api/tenants/" + tenant + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/fulfillment";
  }

  private static Map<String, Object> settings(
      boolean pickup, String instructions, boolean shipping, String amount, String area, long v) {
    Map<String, Object> result = new HashMap<>();
    result.put("pickupEnabled", pickup);
    result.put("pickupInstructions", instructions);
    result.put("fixedShippingEnabled", shipping);
    result.put("fixedShippingAmount", amount);
    result.put("fixedShippingArea", area);
    result.put("version", v);
    return result;
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
