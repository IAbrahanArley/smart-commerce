package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
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
 * SC-020 over HTTP: the OWNER records and corrects expenses with session and CSRF; each one enters
 * the ledger once, a cancellation adds its reversal and keeps the trail, a retry reuses the same
 * expense, and another store's OWNER sees and changes nothing.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class ExpenseIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic expenses integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID ownerA;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = activeIdentity("despesas-a@example.test");
    UUID ownerB = activeIdentity("despesas-b@example.test");
    tenantA = store(ownerA, "despesas-a");
    tenantB = store(ownerB, "despesas-b");
  }

  @Test
  void ownerRecordsCancelsAndCorrectsExpensesWithAnAuditTrailAndLedgerEntries() throws Exception {
    Browser a = login("despesas-a@example.test");
    String request = UUID.randomUUID().toString();
    var created =
        a.post(
            path(tenantA),
            expense(request, "RENT", "FIXED", "1500.00", "2026-10-01", "2026-10-05"));
    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode rent = JSON.readTree(created.body());
    assertThat(rent.get("amount").asString()).isEqualTo("1500.00");
    assertThat(rent.get("status").asString()).isEqualTo("ACTIVE");
    UUID rentId = UUID.fromString(rent.get("id").asString());

    // A retry of the same submission is the same expense; another body under its key conflicts.
    assertThat(
            JSON.readTree(
                    a.post(
                            path(tenantA),
                            expense(
                                request, "RENT", "FIXED", "1500.00", "2026-10-01", "2026-10-05"))
                        .body())
                .get("id")
                .asString())
        .isEqualTo(rentId.toString());
    var conflict =
        a.post(
            path(tenantA),
            expense(request, "RENT", "FIXED", "1600.00", "2026-10-01", "2026-10-05"));
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(conflict.body()).get("code").asString()).isEqualTo("REQUEST_CONFLICT");

    String bags = UUID.randomUUID().toString();
    a.post(path(tenantA), expense(bags, "PACKAGING", "VARIABLE", "80.40", "2026-10-10", null));
    a.post(
        path(tenantA),
        expense(
            UUID.randomUUID().toString(), "MARKETING", "VARIABLE", "200.00", "2026-09-30", null));

    JsonNode october = JSON.readTree(a.get(path(tenantA) + "?month=2026-10").body());
    assertThat(october.get("total").asString()).isEqualTo("1580.40");
    assertThat(october.get("fixed").asString()).isEqualTo("1500.00");
    assertThat(october.get("variable").asString()).isEqualTo("80.40");
    assertThat(october.get("expenses")).hasSize(2);

    // Correction: cancel with a reason (kept, with who and why) and record the right value.
    var cancelled =
        a.post(
            path(tenantA) + "/" + rentId + "/cancel",
            JSON.writeValueAsString(Map.of("reason", "Valor digitado errado")));
    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(cancelled.body()).get("status").asString()).isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject("SELECT cancelled_by FROM expense WHERE id=?", UUID.class, rentId))
        .isEqualTo(ownerA);
    // Cancelling again changes nothing.
    a.post(
        path(tenantA) + "/" + rentId + "/cancel",
        JSON.writeValueAsString(Map.of("reason", "De novo")));
    a.post(
        path(tenantA),
        expense(
            UUID.randomUUID().toString(), "RENT", "FIXED", "1450.00", "2026-10-01", "2026-10-05"));

    october = JSON.readTree(a.get(path(tenantA) + "?month=2026-10").body());
    assertThat(october.get("total").asString()).isEqualTo("1530.40");
    assertThat(october.get("expenses")).hasSize(3); // the cancelled one stays visible

    // Ledger: one EXPENSE per expense and one EXPENSE_REVERSAL for the cancelled one, same dates.
    List<Map<String, Object>> rentEntries =
        jdbc.queryForList(
            "SELECT type,amount,competence_at,settled_at FROM finance_entry WHERE expense_id=? ORDER BY type",
            rentId);
    assertThat(rentEntries).hasSize(2);
    assertThat(rentEntries.get(0).get("type")).isEqualTo("EXPENSE");
    assertThat(rentEntries.get(0).get("amount").toString()).isEqualTo("-1500.00");
    assertThat(rentEntries.get(1).get("type")).isEqualTo("EXPENSE_REVERSAL");
    assertThat(rentEntries.get(1).get("amount").toString()).isEqualTo("1500.00");
    assertThat(rentEntries.get(1).get("competence_at"))
        .isEqualTo(rentEntries.get(0).get("competence_at"));
    assertThat(rentEntries.get(1).get("settled_at"))
        .isEqualTo(rentEntries.get(0).get("settled_at"));
    // Competence starts at midnight in São Paulo (03:00 UTC); unpaid has no settlement.
    assertThat(
            jdbc.queryForObject(
                "SELECT to_char(competence_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI') FROM finance_entry"
                    + " WHERE expense_id=(SELECT id FROM expense WHERE request_id=?::uuid)",
                String.class,
                bags))
        .isEqualTo("2026-10-10 03:00");
    assertThat(
            jdbc.queryForObject(
                    "SELECT sum(amount) FROM finance_entry WHERE tenant_id=? AND type IN ('EXPENSE','EXPENSE_REVERSAL')"
                        + " AND competence_at >= '2026-10-01 03:00+00' AND competence_at < '2026-11-01 03:00+00'",
                    java.math.BigDecimal.class,
                    tenantA)
                .toPlainString())
        .isEqualTo("-1530.40");
  }

  @Test
  void invalidDataCsrfSessionAndOtherStoresAreRefused() throws Exception {
    Browser a = login("despesas-a@example.test");
    Browser b = login("despesas-b@example.test");
    for (String body :
        List.of(
            expense(UUID.randomUUID().toString(), "TAXES", "FIXED", "10.00", "2026-10-01", null),
            expense(UUID.randomUUID().toString(), "RENT", "FIXED", "0", "2026-10-01", null),
            expense(UUID.randomUUID().toString(), "RENT", "FIXED", "10.001", "2026-10-01", null),
            expense(UUID.randomUUID().toString(), "RENT", "FIXED", "10.00", "2026-13-01", null),
            "{}",
            "not json")) assertThat(a.post(path(tenantA), body).statusCode()).isEqualTo(400);
    assertThat(a.get(path(tenantA) + "?month=2026-1").statusCode()).isEqualTo(400);

    String valid =
        expense(UUID.randomUUID().toString(), "RENT", "FIXED", "10.00", "2026-10-01", null);
    assertThat(a.send("POST", path(tenantA), valid, null).statusCode()).isEqualTo(403); // no CSRF
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);

    var mine = JSON.readTree(a.post(path(tenantA), valid).body());
    String id = mine.get("id").asString();
    // B cannot list, create in, or cancel A's expenses: one 404, nothing changes.
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(
            b.post(
                    path(tenantA),
                    expense(
                        UUID.randomUUID().toString(), "RENT", "FIXED", "1.00", "2026-10-01", null))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.post(
                    path(tenantA) + "/" + id + "/cancel",
                    JSON.writeValueAsString(Map.of("reason", "Tentativa")))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.post(
                    path(tenantB) + "/" + id + "/cancel",
                    JSON.writeValueAsString(Map.of("reason", "Tentativa")))
                .statusCode())
        .isEqualTo(404);
    assertThat(jdbc.queryForObject("SELECT status FROM expense WHERE id=?::uuid", String.class, id))
        .isEqualTo("ACTIVE");
    assertThat(JSON.readTree(b.get(path(tenantB) + "?month=2026-10").body()).get("expenses"))
        .isEmpty();
  }

  @Test
  void concurrentSubmissionsOfOneRequestCreateOneExpenseAndOneEntry() throws Exception {
    Browser a = login("despesas-a@example.test");
    String body =
        expense(UUID.randomUUID().toString(), "OTHER", "VARIABLE", "9.90", "2026-10-02", null);
    CyclicBarrier barrier = new CyclicBarrier(4);
    List<Callable<Integer>> work = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++)
      work.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return a.post(path(tenantA), body).statusCode();
          });
    try (var executor = Executors.newFixedThreadPool(4)) {
      for (var result : executor.invokeAll(work)) assertThat(result.get()).isEqualTo(200);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM expense", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_entry", Integer.class))
        .isEqualTo(1);
  }

  private static String expense(
      String request, String category, String kind, String amount, String competence, String paid)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request);
    body.put("category", category);
    body.put("kind", kind);
    body.put("description", "Despesa sintética");
    body.put("amount", amount);
    body.put("competenceDate", competence);
    body.put("paidOn", paid);
    return JSON.writeValueAsString(body);
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/expenses";
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
                .send(
                    "POST",
                    "/api/auth/login",
                    JSON.writeValueAsString(Map.of("email", email, "password", PASSWORD)),
                    browser.csrfToken)
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    return browser;
  }

  private final class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
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

    HttpResponse<String> post(String path, String body) throws Exception {
      return send("POST", path, body, csrfToken);
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
