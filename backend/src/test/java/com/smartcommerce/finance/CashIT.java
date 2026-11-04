package com.smartcommerce.finance;

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
 * SC-052 over HTTP: the store's employee opens, moves and blind-closes a drawer session; the
 * difference adjusts the drawer and becomes a ledger line only the OWNER reads; closed sessions
 * take nothing more; only the OWNER reopens; another store's people see and change nothing.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class CashIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic cash integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    UUID ownerA = activeIdentity("dona-caixa@example.test");
    UUID staffA = activeIdentity("operador-caixa@example.test");
    UUID ownerB = activeIdentity("dono-outro@example.test");
    tenantA = store(ownerA, "caixa-a");
    tenantB = store(ownerB, "caixa-b");
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'STAFF', now())",
        tenantA,
        staffA);
  }

  @Test
  void anEmployeeRunsTheDrawerBlindAndTheOwnerSeesTheDifference() throws Exception {
    Browser owner = login("dona-caixa@example.test");
    Browser staff = login("operador-caixa@example.test");
    JsonNode overview = json(staff.get(path(tenantA, "")));
    assertThat(overview.get("role").asString()).isEqualTo("STAFF");
    String drawer = overview.get("drawers").get(0).get("id").asString();
    assertThat(overview.get("drawers").get(0).get("session").isNull()).isTrue();
    // The bank account the drops go to; employees see accounts by name only.
    String bank =
        json(owner.post(
                "/api/tenants/" + tenantA + "/finance/accounts",
                body("requestId", UUID.randomUUID(), "type", "BANK", "name", "Banco da loja")))
            .get("id")
            .asString();
    assertThat(json(staff.get(path(tenantA, ""))).get("destinations").toString())
        .contains("Banco da loja")
        .doesNotContain("balance");

    // First count on an untouched drawer is its opening balance, not a difference.
    String openRequest = UUID.randomUUID().toString();
    var opened =
        staff.post(
            path(tenantA, "/sessions"),
            body("requestId", openRequest, "accountId", drawer, "amount", "200.00"));
    assertThat(opened.statusCode()).isEqualTo(200);
    String session = json(opened).get("id").asString();
    assertThat(
            json(staff.post(
                    path(tenantA, "/sessions"),
                    body("requestId", openRequest, "accountId", drawer, "amount", "200.00")))
                .get("id")
                .asString())
        .isEqualTo(session);
    assertThat(
            staff
                .post(
                    path(tenantA, "/sessions"),
                    body("requestId", UUID.randomUUID(), "accountId", drawer, "amount", "1.00"))
                .statusCode())
        .isEqualTo(409);

    String moves = path(tenantA, "/sessions/" + session + "/movements");
    String drop = UUID.randomUUID().toString();
    Map<String, Object> toBank =
        map("requestId", drop, "type", "DROP", "amount", "50.00", "reason", "Depósito no banco");
    toBank.put("accountId", bank);
    assertThat(staff.post(moves, JSON.writeValueAsString(toBank)).statusCode()).isEqualTo(200);
    assertThat(staff.post(moves, JSON.writeValueAsString(toBank)).statusCode()).isEqualTo(200);
    staff.post(
        moves,
        body(
            "requestId",
            UUID.randomUUID(),
            "type",
            "DROP",
            "amount",
            "30.00",
            "reason",
            "Retirada da dona"));
    staff.post(
        moves,
        body(
            "requestId",
            UUID.randomUUID(),
            "type",
            "TOP_UP",
            "amount",
            "10.00",
            "reason",
            "Troco trazido pela dona"));
    Map<String, Object> courier =
        map(
            "requestId",
            UUID.randomUUID(),
            "type",
            "EXPENSE",
            "amount",
            "15.00",
            "reason",
            "Motoboy");
    courier.put("category", "SHIPPING");
    JsonNode afterExpense = json(staff.post(moves, JSON.writeValueAsString(courier)));
    assertThat(afterExpense.get("movements")).hasSize(5);
    assertThat(afterExpense.get("expectedAmount").isNull()).isTrue();
    assertThat(balance(bank)).isEqualTo("50.00");
    assertThat(balance(drawer)).isEqualTo("115.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT kind || ' ' || paid_on::text FROM expense WHERE description='Motoboy'",
                String.class))
        .isEqualTo("VARIABLE " + today());

    // Blind close: 110 counted against 115 expected.
    String closeRequest = UUID.randomUUID().toString();
    var closed =
        staff.post(
            path(tenantA, "/sessions/" + session + "/close"),
            body("requestId", closeRequest, "counted", "110.00"));
    assertThat(closed.statusCode()).isEqualTo(200);
    assertThat(json(closed).get("status").asString()).isEqualTo("CLOSED");
    assertThat(json(closed).get("expectedAmount").isNull()).isTrue();
    assertThat(json(closed).get("difference").isNull()).isTrue();
    // Blind also in the list: the difference row would give the expected amount away.
    assertThat(json(closed).get("movements").toString()).doesNotContain("CASH_DIFFERENCE");
    assertThat(balance(drawer)).isEqualTo("110.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT amount::text FROM finance_entry WHERE type='CASH_DIFFERENCE'",
                String.class))
        .isEqualTo("-5.00");

    // Closing again: the same request is the same close; another one is refused.
    assertThat(
            staff
                .post(
                    path(tenantA, "/sessions/" + session + "/close"),
                    body("requestId", closeRequest, "counted", "110.00"))
                .statusCode())
        .isEqualTo(200);
    var twice =
        staff.post(
            path(tenantA, "/sessions/" + session + "/close"),
            body("requestId", UUID.randomUUID(), "counted", "110.00"));
    assertThat(twice.statusCode()).isEqualTo(409);
    assertThat(json(twice).get("code").asString()).isEqualTo("SESSION_CLOSED");
    var late =
        staff.post(
            moves,
            body(
                "requestId",
                UUID.randomUUID(),
                "type",
                "DROP",
                "amount",
                "1.00",
                "reason",
                "Depois do fechamento"));
    assertThat(late.statusCode()).isEqualTo(409);

    // Only the OWNER reads the closed session, the history and the report line.
    assertThat(staff.get(path(tenantA, "/sessions/" + session)).statusCode()).isEqualTo(404);
    assertThat(staff.get(path(tenantA, "/sessions")).statusCode()).isEqualTo(404);
    JsonNode seen = json(owner.get(path(tenantA, "/sessions/" + session)));
    assertThat(seen.get("expectedAmount").asString()).isEqualTo("115.00");
    assertThat(seen.get("difference").asString()).isEqualTo("-5.00");
    assertThat(seen.get("movements").toString()).contains("CASH_DIFFERENCE");
    assertThat(seen.get("closedBy").asString()).isEqualTo("operador-caixa@example.test");
    JsonNode history = json(owner.get(path(tenantA, "/sessions")));
    assertThat(history.get("sessions")).hasSize(1);
    assertThat(history.get("nextCursor").isNull()).isTrue();
    assertThat(owner.get(path(tenantA, "/sessions?before=junk")).statusCode()).isEqualTo(400);
    assertThat(
            json(owner.get("/api/tenants/" + tenantA + "/finance"))
                .get("cashDifferences")
                .asString())
        .isEqualTo("-5.00");

    // A later opening that does not match the drawer is a difference too.
    String next =
        json(staff.post(
                path(tenantA, "/sessions"),
                body("requestId", UUID.randomUUID(), "accountId", drawer, "amount", "100.00")))
            .get("id")
            .asString();
    assertThat(
            jdbc.queryForObject(
                "SELECT amount::text FROM finance_entry WHERE type='CASH_DIFFERENCE'"
                    + " AND cash_session_id=?::uuid",
                String.class,
                next))
        .isEqualTo("-10.00");
  }

  @Test
  void onlyTheOwnerReopensAndTheDatabaseGuardsClosedSessions() throws Exception {
    Browser owner = login("dona-caixa@example.test");
    Browser staff = login("operador-caixa@example.test");
    String drawer = json(staff.get(path(tenantA, ""))).get("drawers").get(0).get("id").asString();

    // Four simultaneous openings of one drawer: exactly one session.
    CyclicBarrier barrier = new CyclicBarrier(4);
    List<Callable<Integer>> work = new ArrayList<>();
    for (int i = 0; i < 4; i++)
      work.add(
          () -> {
            String opening =
                body("requestId", UUID.randomUUID(), "accountId", drawer, "amount", "0.00");
            barrier.await(10, TimeUnit.SECONDS);
            return staff.post(path(tenantA, "/sessions"), opening).statusCode();
          });
    List<Integer> statuses = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(4)) {
      for (var result : executor.invokeAll(work)) statuses.add(result.get());
    }
    assertThat(statuses).containsOnly(200, 409).filteredOn(code -> code == 200).hasSize(1);
    // Parallel requests on one login race in the JDBC session and leave its CSRF token stale for
    // the next ones (a test artifact: people do not fire them at once); go on with a fresh login.
    Browser operator = login("operador-caixa@example.test");
    String session =
        jdbc.queryForObject("SELECT id::text FROM cash_session WHERE status='OPEN'", String.class);
    operator.post(
        path(tenantA, "/sessions/" + session + "/close"),
        body("requestId", UUID.randomUUID(), "counted", "0.00"));

    // The database refuses movements in a closed session and any change to it.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO account_movement(id,tenant_id,account_id,movement_key,kind,amount,"
                        + "occurred_on,description,cash_session_id,created_by,created_at)"
                        + " SELECT gen_random_uuid(),tenant_id,account_id,'x','OWNER_WITHDRAWAL',-1,"
                        + "current_date,'Teste',id,opened_by,now() FROM cash_session WHERE id=?::uuid",
                    session))
        .hasMessageContaining("cash session closed");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE cash_session SET status='OPEN', closed_at=NULL WHERE id=?::uuid",
                    session))
        .hasMessageContaining("only closes once");

    String reopen = path(tenantA, "/sessions/" + session + "/reopen");
    assertThat(operator.post(reopen, body("reason", "Esqueci uma venda")).statusCode())
        .isEqualTo(404);
    var reopened = owner.post(reopen, body("reason", "Esqueci uma venda"));
    assertThat(reopened.statusCode()).isEqualTo(200);
    JsonNode again = json(reopened);
    assertThat(again.get("status").asString()).isEqualTo("OPEN");
    assertThat(again.get("reopenedFrom").asString()).isEqualTo(session);
    assertThat(json(owner.post(reopen, body("reason", "De novo"))).get("id").asString())
        .isEqualTo(again.get("id").asString());

    // Another store's OWNER sees nothing of A and cannot touch its sessions.
    Browser other = login("dono-outro@example.test");
    assertThat(other.get(path(tenantA, "")).statusCode()).isEqualTo(404);
    assertThat(
            other
                .post(
                    path(tenantA, "/sessions/" + again.get("id").asString() + "/close"),
                    body("requestId", UUID.randomUUID(), "counted", "0.00"))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            other
                .post(
                    path(tenantB, "/sessions"),
                    body("requestId", UUID.randomUUID(), "accountId", drawer, "amount", "0.00"))
                .statusCode())
        .isEqualTo(404);
    // Invalid bodies are 400; an unknown type, negative money or a category on a drop.
    String moves = path(tenantA, "/sessions/" + again.get("id").asString() + "/movements");
    for (String invalid :
        List.of(
            body(
                "requestId",
                UUID.randomUUID(),
                "type",
                "STEAL",
                "amount",
                "1.00",
                "reason",
                "Teste"),
            body("requestId", UUID.randomUUID(), "type", "DROP", "amount", "-1", "reason", "Teste"),
            body(
                "requestId",
                UUID.randomUUID(),
                "type",
                "DROP",
                "amount",
                "1.00",
                "reason",
                "Teste",
                "category",
                "OTHER"),
            body(
                "requestId",
                UUID.randomUUID(),
                "type",
                "EXPENSE",
                "amount",
                "1.00",
                "reason",
                "Teste"))) {
      var refused = operator.post(moves, invalid);
      assertThat(refused.statusCode()).as(invalid + " -> " + refused.body()).isEqualTo(400);
    }
  }

  private String today() {
    return jdbc.queryForObject(
        "SELECT (now() AT TIME ZONE 'America/Sao_Paulo')::date::text", String.class);
  }

  private String balance(String account) {
    return jdbc.queryForObject(
        "SELECT COALESCE(sum(amount),0)::numeric(20,2)::text FROM account_movement WHERE account_id=?::uuid",
        String.class,
        account);
  }

  private static String path(UUID tenant, String rest) {
    return "/api/tenants/" + tenant + "/cash" + rest;
  }

  private static String body(Object... pairs) throws Exception {
    return JSON.writeValueAsString(map(pairs));
  }

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2)
      body.put((String) pairs[i], String.valueOf(pairs[i + 1]));
    return body;
  }

  private static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
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
