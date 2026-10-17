package com.smartcommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.smartcommerce.tenant.entities.Tenant;
import com.smartcommerce.tenant.repositories.TenantAccessRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Session, CSRF, monetary contracts and tenant isolation against a disposable PostgreSQL. */
@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogIsolationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic catalog integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired TenantAccessRepository access;
  @PersistenceContext EntityManager entities;
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
  void developmentApiDocumentationIsAuthenticatedAndDescribesCurrentContracts() throws Exception {
    Browser anonymous = new Browser();
    assertThat(anonymous.get("/v3/api-docs").statusCode()).isEqualTo(403);
    assertThat(anonymous.get("/swagger-ui/index.html").statusCode()).isEqualTo(403);
    Browser authenticated = login("catalog-a@example.test");
    var response = authenticated.get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode spec = JSON.readTree(response.body());
    assertThat(spec.at("/info/title").asText()).isEqualTo("Smart Commerce API");
    assertThat(spec.get("paths").has("/api/tenants/{tenantId}/products")).isTrue();
    assertThat(spec.at("/components/securitySchemes/session/name").asText()).isEqualTo("SCSESSION");
    assertThat(
            spec.at("/components/schemas/LoginRequest/properties/password/writeOnly").asBoolean())
        .isTrue();
    assertThat(authenticated.get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    var config = JSON.readTree(authenticated.get("/v3/api-docs/swagger-config").body());
    assertThat(config.get("supportedSubmitMethods").size()).isZero();
    assertThat(config.get("persistAuthorization").asBoolean()).isFalse();
  }

  @Test
  void jpaAndJdbcShareConnectionRollbackAndAuthorizationLocks() {
    assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
    UUID jpaId = UUID.randomUUID();
    UUID jdbcId = UUID.randomUUID();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              entities.persist(new Tenant(jpaId, "Mixed transaction JPA", Instant.now()));
              entities.flush();
              jdbc.update(
                  "INSERT INTO tenant(id,company_name,created_at) VALUES(?, 'Mixed transaction JDBC', now())",
                  jdbcId);
              Number jpaPid =
                  (Number)
                      entities
                          .createNativeQuery("SELECT pg_backend_pid()", Integer.class)
                          .getSingleResult();
              assertThat(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class))
                  .isEqualTo(jpaPid.intValue());
              assertThat(access.lockOwnedStore(ownerA, tenantA)).isPresent();
              // An independent connection must not revoke membership while the JPA transaction owns
              // its JDBC lock.
              try (var other =
                      DriverManager.getConnection(
                          POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                  var setting = other.createStatement();
                  var revoke =
                      other.prepareStatement(
                          "DELETE FROM tenant_membership WHERE tenant_id=? AND user_id=?")) {
                setting.execute("SET lock_timeout = '250ms'");
                revoke.setObject(1, tenantA);
                revoke.setObject(2, ownerA);
                assertThatThrownBy(() -> revoke.executeUpdate())
                    .isInstanceOfSatisfying(
                        SQLException.class,
                        failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
              } catch (SQLException failure) {
                throw new AssertionError("Mixed transaction lock verification failed", failure);
              }
              status.setRollbackOnly();
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tenant WHERE id IN (?,?)", Long.class, jpaId, jdbcId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tenant_membership WHERE tenant_id=? AND user_id=?",
                Long.class,
                tenantA,
                ownerA))
        .isEqualTo(1L);
  }

  @Test
  void createsListsReadsEditsAndPreservesExactMoneyAndOptionalCost() throws Exception {
    Browser a = login("catalog-a@example.test");
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).size()).isZero();
    var created =
        a.post(path(tenantA), product(" Produto ç ", " Ref/01 ", "12345678901234567.89", null));
    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode initial = JSON.readTree(created.body());
    String id = initial.get("id").asText();
    assertThat(initial.get("name").asText()).isEqualTo("Produto ç");
    assertThat(initial.get("sku").asText()).isEqualTo("Ref/01");
    assertThat(initial.get("price").isString()).isTrue();
    assertThat(initial.get("price").asText()).isEqualTo("12345678901234567.89");
    assertThat(initial.get("cost").isNull()).isTrue();
    assertThat(initial.get("currency").asText()).isEqualTo("BRL");
    assertThat(a.get(path(tenantA) + "/" + id).statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get(0)).isEqualTo(initial);
    Map<String, Object> edit = product("Atualizado", "SKU-2", "0.01", "0.00");
    edit.put("version", initial.get("version").asLong());
    var updated = a.put(path(tenantA) + "/" + id, edit);
    assertThat(updated.statusCode()).isEqualTo(200);
    JsonNode current = JSON.readTree(updated.body());
    assertThat(current.get("version").asLong()).isEqualTo(initial.get("version").asLong() + 1);
    assertThat(current.get("price").asText()).isEqualTo("0.01");
    assertThat(current.get("cost").asText()).isEqualTo("0.00");
    assertThat(JSON.readTree(a.get(path(tenantA) + "/" + id).body())).isEqualTo(current);
    var stale = a.put(path(tenantA) + "/" + id, edit);
    assertThat(stale.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(stale.body()).get("code").asText()).isEqualTo("VERSION_CONFLICT");
    edit.remove("version");
    assertThat(a.put(path(tenantA) + "/" + id, edit).statusCode()).isEqualTo(400);
    edit.put("version", "1");
    assertThat(a.put(path(tenantA) + "/" + id, edit).statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(a.get(path(tenantA) + "/" + id).body())).isEqualTo(current);
  }

  @Test
  void twoOwnersCannotListReadCreateOrEditAcrossTenantsEvenWithForgedFields() throws Exception {
    Browser a = login("catalog-a@example.test");
    Browser b = login("catalog-b@example.test");
    String productA =
        JSON.readTree(a.post(path(tenantA), product("A", "SKU", "20.00", null)).body())
            .get("id")
            .asText();
    String productB =
        JSON.readTree(b.post(path(tenantB), product("B", "SKU", "30.00", null)).body())
            .get("id")
            .asText();
    Map<String, Object> attack = product("Attack", "ATTACK", "1.00", null);
    attack.put("version", 0);
    attack.put("tenantId", tenantB.toString());
    attack.put("userId", ownerB.toString());
    attack.put("role", "ADMIN");
    attack.put("currency", "USD");
    var outside = a.get(path(tenantB));
    var absent = a.get(path(UUID.randomUUID()));
    assertThat(outside.statusCode()).isEqualTo(404);
    assertThat(outside.body()).isEqualTo(absent.body());
    assertThat(a.get(path(tenantB) + "/" + productB).statusCode()).isEqualTo(404);
    assertThat(a.get(path(tenantA) + "/" + productB).statusCode()).isEqualTo(404);
    assertThat(b.get(path(tenantB) + "/" + productA).statusCode()).isEqualTo(404);
    assertThat(a.post(path(tenantB), attack).statusCode()).isEqualTo(404);
    assertThat(a.put(path(tenantB) + "/" + productB, attack).statusCode()).isEqualTo(404);
    assertThat(a.put(path(tenantA) + "/" + productB, attack).statusCode()).isEqualTo(404);
    var own = a.post(path(tenantA), attack);
    assertThat(own.statusCode()).isEqualTo(201);
    assertThat(JSON.readTree(own.body()).get("currency").asText()).isEqualTo("BRL");
    assertThat(JSON.readTree(b.get(path(tenantB)).body()).size()).isEqualTo(1);
    jdbc.update(
        "DELETE FROM tenant_membership WHERE tenant_id = ? AND user_id = ?", tenantA, ownerA);
    assertThat(a.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(a.get(path(tenantA) + "/" + productA).statusCode()).isEqualTo(404);
    assertThat(a.post(path(tenantA), attack).statusCode()).isEqualTo(404);
    assertThat(a.put(path(tenantA) + "/" + productA, attack).statusCode()).isEqualTo(404);
  }

  @Test
  void requiresSessionCsrfAndSameOriginForMutations() throws Exception {
    Browser anonymous = new Browser();
    var body = product("A", "SKU", "1.00", null);
    assertThat(anonymous.get(path(tenantA)).statusCode()).isEqualTo(401);
    assertThat(anonymous.post(path(tenantA), body).statusCode()).isEqualTo(403);
    anonymous.csrf();
    assertThat(anonymous.post(path(tenantA), body).statusCode()).isEqualTo(401);
    Browser a = login("catalog-a@example.test");
    String json = JSON.writeValueAsString(body);
    assertThat(a.send("POST", path(tenantA), json, null, null).statusCode()).isEqualTo(403);
    assertThat(a.send("POST", path(tenantA), json, "incorrect", null).statusCode()).isEqualTo(403);
    assertThat(
            a.send("POST", path(tenantA), json, a.csrfToken, "https://attacker.example")
                .statusCode())
        .isEqualTo(403);
    String id = JSON.readTree(a.post(path(tenantA), body).body()).get("id").asText();
    assertThat(a.send("PUT", path(tenantA) + "/" + id, json, null, null).statusCode())
        .isEqualTo(403);
  }

  @Test
  void rejectsInvalidPrecisionTypesNamesAndSkuWithoutPersistingPartialData() throws Exception {
    Browser a = login("catalog-a@example.test");
    for (Object price :
        new Object[] {"0", "-1.00", "1.001", "1e2", "100000000000000000.00", 12.50}) {
      assertThat(a.post(path(tenantA), product("A", "SKU", price, null)).statusCode())
          .as("price %s", price)
          .isEqualTo(400);
    }
    for (Object cost : new Object[] {"-0.01", "0.001", 10}) {
      assertThat(a.post(path(tenantA), product("A", "SKU", "1.00", cost)).statusCode())
          .as("cost %s", cost)
          .isEqualTo(400);
    }
    for (String name : new String[] {" ", "x".repeat(121), "line\nbreak", "hidden\u202Etext"}) {
      assertThat(a.post(path(tenantA), product(name, "SKU", "1.00", null)).statusCode())
          .isEqualTo(400);
    }
    for (String sku : new String[] {" ", "x".repeat(65), "SKU espaço", "ç", "<script>"}) {
      assertThat(a.post(path(tenantA), product("A", sku, "1.00", null)).statusCode())
          .isEqualTo(400);
    }
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).size()).isZero();
    assertThat(a.post(path(tenantA), product("A", "SKU", "1.00", null)).statusCode())
        .isEqualTo(201);
    assertThat(a.post(path(tenantA), product("B", "sku", "1", null)).statusCode()).isEqualTo(201);
  }

  @Test
  void concurrentSkuConflictHasOneWinnerAndDoesNotLeakSql() throws Exception {
    Browser a = login("catalog-a@example.test");
    Browser a2 = login("catalog-a@example.test");
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return a.post(path(tenantA), product("First", "RACE", "1", null));
              });
      var second =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return a2.post(path(tenantA), product("Second", "RACE", "2", null));
              });
      var responses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
      assertThat(responses.stream().map(HttpResponse::statusCode).toList())
          .containsExactlyInAnyOrder(201, 409);
      String conflict =
          responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow().body();
      assertThat(conflict)
          .doesNotContain("INSERT", "duplicate", "constraint", "org.postgresql", "RACE");
      assertThat(JSON.readTree(conflict).get("code").asText()).isEqualTo("SKU_CONFLICT");
    }
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).size()).isEqualTo(1);
    Browser b = login("catalog-b@example.test");
    assertThat(b.post(path(tenantB), product("Other tenant", "RACE", "3", null)).statusCode())
        .isEqualTo(201);
  }

  @Test
  void databaseRejectsCrossTenantStoreReferencesAndConcurrentEditHasOneWinner() throws Exception {
    UUID storeB =
        jdbc.queryForObject("SELECT id FROM store WHERE tenant_id = ?", UUID.class, tenantB);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO catalog_product (id, tenant_id, store_id, name, sku, price, created_at, updated_at) VALUES (?, ?, ?, 'Invalid relation', 'FK', 1.00, now(), now())",
                    UUID.randomUUID(),
                    tenantA,
                    storeB))
        .isInstanceOf(DataIntegrityViolationException.class);
    Browser a = login("catalog-a@example.test");
    Browser a2 = login("catalog-a@example.test");
    JsonNode initial =
        JSON.readTree(a.post(path(tenantA), product("Initial", "EDIT", "1", null)).body());
    UUID id = UUID.fromString(initial.get("id").asText());
    for (String value : new String[] {"1.235", "0", "-1", "100000000000000000"}) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE catalog_product SET price = ?::numeric WHERE id = ?", value, id))
          .isInstanceOf(DataIntegrityViolationException.class)
          .satisfies(
              error ->
                  assertThat(((java.sql.SQLException) error.getCause()).getSQLState())
                      .isEqualTo("23514"));
    }
    assertThatThrownBy(
            () -> jdbc.update("UPDATE catalog_product SET cost = 1.235 WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class)
        .satisfies(
            error ->
                assertThat(((java.sql.SQLException) error.getCause()).getSQLState())
                    .isEqualTo("23514"));
    assertThat(
            jdbc.queryForObject(
                "SELECT price FROM catalog_product WHERE id = ?", java.math.BigDecimal.class, id))
        .isEqualByComparingTo("1.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT cost FROM catalog_product WHERE id = ?", java.math.BigDecimal.class, id))
        .isNull();
    String productPath = path(tenantA) + "/" + initial.get("id").asText();
    Map<String, Object> firstEdit = product("First edit", "EDIT", "2", null);
    Map<String, Object> secondEdit = product("Second edit", "EDIT", "3", null);
    firstEdit.put("version", initial.get("version").asLong());
    secondEdit.put("version", initial.get("version").asLong());
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return a.put(productPath, firstEdit);
              });
      var second =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return a2.put(productPath, secondEdit);
              });
      var responses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
      assertThat(responses.stream().map(HttpResponse::statusCode).toList())
          .containsExactlyInAnyOrder(200, 409);
      JsonNode winner =
          JSON.readTree(
              responses.stream()
                  .filter(r -> r.statusCode() == 200)
                  .findFirst()
                  .orElseThrow()
                  .body());
      assertThat(JSON.readTree(a.get(productPath).body())).isEqualTo(winner);
      assertThat(winner.get("version").asLong()).isEqualTo(initial.get("version").asLong() + 1);
    }
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
