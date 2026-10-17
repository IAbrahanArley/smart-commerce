package com.smartcommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real session cookies, CSRF, HTTP authorization and PostgreSQL transactions across two owners. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic tenant integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID ownerA;
  UUID ownerB;

  @BeforeEach
  void resetDisposableFixtures() {
    // Each class owns a disposable PostgreSQL container; never clean the developer database.
    for (String table :
        new String[] {
          "store",
          "tenant_membership",
          "tenant",
          "SPRING_SESSION",
          "identity_email_verification",
          "identity_rate_limit",
          "identity_local_credential",
          "identity_user"
        }) jdbc.update("DELETE FROM " + table);
    ownerA = activeIdentity("owner-a@example.test");
    ownerB = activeIdentity("owner-b@example.test");
  }

  @Test
  void ownersCreateDraftStoresAndCanOnlyListAndReadTheirOwnTenant() throws Exception {
    Browser a = login("owner-a@example.test");
    Browser b = login("owner-b@example.test");
    assertThat(JSON.readTree(a.get("/api/tenants").body()).size()).isZero();
    var createdA = a.post("/api/tenants", creation("Empresa A", "Loja A", "loja-a"));
    var createdB = b.post("/api/tenants", creation("Empresa B", "Loja B", "loja-b"));
    assertThat(createdA.statusCode()).isEqualTo(201);
    assertThat(createdB.statusCode()).isEqualTo(201);
    JsonNode tenantA = JSON.readTree(createdA.body());
    JsonNode tenantB = JSON.readTree(createdB.body());
    String idA = tenantA.get("id").asText();
    String idB = tenantB.get("id").asText();
    assertThat(UUID.fromString(idA)).isNotEqualTo(UUID.fromString(idB));
    assertThat(createdA.headers().firstValue("Location")).contains("/api/tenants/" + idA);
    assertThat(tenantA.get("companyName").asText()).isEqualTo("Empresa A");
    assertThat(tenantA.get("role").asText()).isEqualTo("OWNER");
    assertThat(tenantA.get("store").get("name").asText()).isEqualTo("Loja A");
    assertThat(tenantA.get("store").get("slug").asText()).isEqualTo("loja-a");
    assertThat(tenantA.get("store").get("status").asText()).isEqualTo("DRAFT");
    assertThat(UUID.fromString(tenantA.get("store").get("id").asText())).isNotNull();
    var own = a.get("/api/tenants/" + idA);
    assertThat(own.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(own.body())).isEqualTo(tenantA);
    JsonNode listA = JSON.readTree(a.get("/api/tenants").body());
    JsonNode listB = JSON.readTree(b.get("/api/tenants").body());
    assertThat(listA.size()).isEqualTo(1);
    assertThat(listB.size()).isEqualTo(1);
    assertThat(listA.get(0)).isEqualTo(tenantA);
    assertThat(listB.get(0)).isEqualTo(tenantB);
    var cross = a.get("/api/tenants/" + idB);
    var absent = a.get("/api/tenants/" + UUID.randomUUID());
    assertThat(cross.statusCode()).isEqualTo(404);
    assertThat(absent.statusCode()).isEqualTo(404);
    assertThat(cross.body()).isEqualTo(absent.body());
    assertThat(b.get("/api/tenants/" + idA).statusCode()).isEqualTo(404);
    assertThat(own.body())
        .doesNotContain("password", "hash", "email", "authVersion", ownerB.toString());
    assertCounts(2);
  }

  @Test
  void missingIdentityCsrfCrossOriginAndOversizedMutationsFailClosed() throws Exception {
    Browser anonymous = new Browser();
    assertThat(anonymous.get("/api/tenants").statusCode()).isEqualTo(401);
    assertThat(anonymous.get("/api/tenants/" + UUID.randomUUID()).statusCode()).isEqualTo(401);
    assertThat(anonymous.post("/api/tenants", creation("A", "A", "aaa")).statusCode())
        .isEqualTo(403);
    anonymous.csrf();
    assertThat(anonymous.post("/api/tenants", creation("A", "A", "aaa")).statusCode())
        .isEqualTo(401);
    Browser owner = login("owner-a@example.test");
    String payload = JSON.writeValueAsString(creation("A", "A", "aaa"));
    assertThat(owner.send("POST", "/api/tenants", payload, null, null, null).statusCode())
        .isEqualTo(403);
    assertThat(owner.send("POST", "/api/tenants", payload, "incorrect", null, null).statusCode())
        .isEqualTo(403);
    assertThat(
            owner
                .send(
                    "POST",
                    "/api/tenants",
                    payload,
                    owner.csrfToken,
                    "https://attacker.example",
                    null)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            owner
                .send("POST", "/api/tenants", payload, owner.csrfToken, null, "cross-site")
                .statusCode())
        .isEqualTo(403);
    assertThat(
            owner
                .send(
                    "POST",
                    "/api/tenants",
                    "{\"companyName\":\"" + "a".repeat(9000) + "\"}",
                    owner.csrfToken,
                    null,
                    null)
                .statusCode())
        .isEqualTo(413);
    assertCounts(0);
  }

  @Test
  void clientSuppliedOwnerTenantRoleAndHeadersNeverAuthorizeAnotherTenant() throws Exception {
    Browser a = login("owner-a@example.test");
    Browser b = login("owner-b@example.test");
    String idB =
        JSON.readTree(b.post("/api/tenants", creation("B", "B", "store-b")).body())
            .get("id")
            .asText();
    var forged =
        Map.of(
            "companyName",
            "A",
            "storeName",
            "A",
            "slug",
            "store-a",
            "userId",
            ownerB.toString(),
            "ownerId",
            ownerB.toString(),
            "tenantId",
            idB,
            "role",
            "ADMIN");
    var response = a.post("/api/tenants", forged);
    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode created = JSON.readTree(response.body());
    String idA = created.get("id").asText();
    assertThat(idA).isNotEqualTo(idB);
    assertThat(created.get("role").asText()).isEqualTo("OWNER");
    assertThat(
            jdbc.queryForObject(
                "SELECT user_id FROM tenant_membership WHERE tenant_id = ?",
                UUID.class,
                UUID.fromString(idA)))
        .isEqualTo(ownerA);
    var tampered =
        a.client.send(
            HttpRequest.newBuilder(
                    a.uri("/api/tenants/" + idB + "?userId=" + ownerB + "&tenantId=" + idA))
                .header("X-Tenant-Id", idB)
                .header("X-User-Id", ownerB.toString())
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(tampered.statusCode()).isEqualTo(404);
    assertThat(b.get("/api/tenants/" + idA).statusCode()).isEqualTo(404);
    assertCounts(2);
  }

  @Test
  void concurrentSlugConflictHasOneWinnerAndRollsBackLosingCompanyAndMembership() throws Exception {
    Browser a = login("owner-a@example.test");
    Browser b = login("owner-b@example.test");
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return a.post("/api/tenants", creation("Race A", "A", "shared-slug"));
              });
      var second =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return b.post("/api/tenants", creation("Race B", "B", "shared-slug"));
              });
      var responses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
      assertThat(responses.stream().map(HttpResponse::statusCode).toList())
          .containsExactlyInAnyOrder(201, 409);
      var loser =
          responses.stream().filter(value -> value.statusCode() == 409).findFirst().orElseThrow();
      assertThat(loser.body())
          .doesNotContain(
              "Race A",
              "Race B",
              "shared-slug",
              "duplicate",
              "constraint",
              "INSERT",
              "org.postgresql");
    }
    assertCounts(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tenant t LEFT JOIN store s ON s.tenant_id = t.id WHERE s.id IS NULL",
                Integer.class))
        .isZero();
    assertThat(
            JSON.readTree(a.get("/api/tenants").body()).size()
                + JSON.readTree(b.get("/api/tenants").body()).size())
        .isEqualTo(1);
  }

  @Test
  void validationRejectsInvalidSlugsAndNamesWithoutPartialPersistence() throws Exception {
    Browser a = login("owner-a@example.test");
    for (String slug :
        new String[] {
          "ab", "loja_a", "-loja", "loja-", "loja--a", "loja espaço", " loja", "a".repeat(64)
        }) {
      assertThat(a.post("/api/tenants", creation("Empresa", "Loja", slug)).statusCode())
          .isEqualTo(400);
    }
    assertThat(a.post("/api/tenants", creation(" ", "Loja", "valid-store")).statusCode())
        .isEqualTo(400);
    assertThat(
            a.post("/api/tenants", creation("Empresa", "a".repeat(121), "valid-store"))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            a.post("/api/tenants", creation("Empresa", "Line\nBreak", "valid-store")).statusCode())
        .isEqualTo(400);
    assertThat(a.post("/api/tenants", Map.of("companyName", "Empresa")).statusCode())
        .isEqualTo(400);
    assertThat(a.get("/api/tenants/not-a-uuid").statusCode()).isEqualTo(400);
    assertCounts(0);
    assertThat(a.post("/api/tenants", creation(" Empresa ", " Loja ", "a".repeat(63))).statusCode())
        .isEqualTo(201);
    assertThat(jdbc.queryForObject("SELECT company_name FROM tenant", String.class))
        .isEqualTo("Empresa");
    assertThat(jdbc.queryForObject("SELECT name FROM store", String.class)).isEqualTo("Loja");
    assertThat(a.post("/api/tenants", creation("Outra", "Outra", "A".repeat(63))).statusCode())
        .isEqualTo(409);
    assertCounts(1);
  }

  @Test
  void membershipIsRecheckedForEveryRequestAndIdentityRevocationRemovesAccess() throws Exception {
    Browser a = login("owner-a@example.test");
    String id =
        JSON.readTree(a.post("/api/tenants", creation("A", "A", "store-a")).body())
            .get("id")
            .asText();
    jdbc.update(
        "DELETE FROM tenant_membership WHERE tenant_id = ? AND user_id = ?",
        UUID.fromString(id),
        ownerA);
    assertThat(a.get("/api/tenants/" + id).statusCode()).isEqualTo(404);
    assertThat(JSON.readTree(a.get("/api/tenants").body()).size()).isZero();
    jdbc.update("UPDATE identity_user SET status = 'DISABLED' WHERE id = ?", ownerA);
    assertThat(a.get("/api/tenants").statusCode()).isEqualTo(401);
    assertThat(a.get("/api/tenants/" + id).statusCode()).isEqualTo(401);
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

  private static Map<String, String> creation(String company, String store, String slug) {
    return Map.of("companyName", company, "storeName", store, "slug", slug);
  }

  private void assertCounts(int expected) {
    for (String table : new String[] {"tenant", "tenant_membership", "store"}) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
          .as(table)
          .isEqualTo(expected);
    }
  }

  private final class Browser {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    URI uri(String path) {
      return URI.create("http://127.0.0.1:" + port + path);
    }

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asText();
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null, null, null);
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
      return send("POST", path, JSON.writeValueAsString(body), csrfToken, null, null);
    }

    HttpResponse<String> send(
        String method, String path, String body, String csrf, String origin, String fetchSite)
        throws Exception {
      var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20));
      if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
      if (origin != null) request.header("Origin", origin);
      if (fetchSite != null) request.header("Sec-Fetch-Site", fetchSite);
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
