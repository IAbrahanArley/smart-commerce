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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
 * SC-050 (ADR-0019 C2) over real HTTP and PostgreSQL: default and OWNER accounts, balances as the
 * sum of movements, idempotent adjustments and transfers, a statement that adds up, a database that
 * refuses to change movements, concurrent transfers, and no access for another store's OWNER or for
 * an employee.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false", "advisor.housekeeping.enabled=false"})
class AccountIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic accounts secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  Browser owner;
  String store;

  @BeforeEach
  void ownerWithStore() throws Exception {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, identity_rate_limit CASCADE");
    activeIdentity("dona@example.test");
    owner = login("dona@example.test");
    store = create(owner, "loja-contas");
  }

  @Test
  void balancesAreTheSumOfIdempotentMovementsAndTheStatementAddsUp() throws Exception {
    JsonNode initial = json(owner.get(path("/accounts")));
    assertThat(initial.size()).isEqualTo(1);
    assertThat(initial.get(0).get("name").asText()).isEqualTo("Caixa da loja");
    assertThat(initial.get(0).get("systemKey").asText()).isEqualTo("DEFAULT_CASH");
    String cash = initial.get(0).get("id").asText();
    connectMercadoPago(store);
    JsonNode withProvider = json(owner.get(path("/accounts")));
    assertThat(withProvider.get(1).get("type").asText()).isEqualTo("PAYMENT_PROVIDER");
    assertThat(withProvider.size()).isEqualTo(2);
    // The Mercado Pago account is read-only: no manual adjustment, no transfer in or out.
    String provider = withProvider.get(1).get("id").asText();
    assertThat(json(adjust(provider, adjustment("10", "Ajuste manual"))).get("code").asText())
        .isEqualTo("PROVIDER_READ_ONLY");
    for (var pair : List.of(List.of(cash, provider), List.of(provider, cash)))
      assertThat(
              json(owner.send(
                      "POST",
                      path("/transfers"),
                      Map.of(
                          "requestId", UUID.randomUUID().toString(),
                          "fromAccountId", pair.get(0),
                          "toAccountId", pair.get(1),
                          "amount", "10",
                          "occurredOn", TODAY)))
                  .get("code")
                  .asText())
          .isEqualTo("PROVIDER_READ_ONLY");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM account_movement", Long.class)).isZero();

    String request = UUID.randomUUID().toString();
    var bank =
        owner.send(
            "POST",
            path("/accounts"),
            Map.of("requestId", request, "type", "BANK", "name", "Banco Inter"));
    assertThat(bank.statusCode()).isEqualTo(200);
    String bankId = json(bank).get("id").asText();
    assertThat(
            json(owner.send(
                    "POST",
                    path("/accounts"),
                    Map.of("requestId", request, "type", "BANK", "name", "Banco Inter")))
                .get("id")
                .asText())
        .isEqualTo(bankId);
    assertThat(
            owner
                .send(
                    "POST",
                    path("/accounts"),
                    Map.of("requestId", request, "type", "BANK", "name", "Outro"))
                .statusCode())
        .isEqualTo(409);

    // Opening balance, retried: one movement only.
    var opening = adjustment("150.00", "Saldo inicial da gaveta");
    assertThat(adjust(cash, opening).statusCode()).isEqualTo(200);
    assertThat(json(adjust(cash, opening)).get("balance").asText()).isEqualTo("150.00");
    opening.put("amount", "151.00");
    assertThat(adjust(cash, opening).statusCode()).isEqualTo(409);

    var transfer =
        new HashMap<String, Object>(
            Map.of(
                "requestId",
                UUID.randomUUID().toString(),
                "fromAccountId",
                cash,
                "toAccountId",
                bankId,
                "amount",
                "50",
                "occurredOn",
                TODAY,
                "note",
                "Depósito no banco"));
    assertThat(owner.send("POST", path("/transfers"), transfer).statusCode()).isEqualTo(200);
    JsonNode afterTransfer = json(owner.send("POST", path("/transfers"), transfer));
    assertThat(balance(afterTransfer, cash)).isEqualByComparingTo("100.00");
    assertThat(balance(afterTransfer, bankId)).isEqualByComparingTo("50.00");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM account_movement", Long.class))
        .isEqualTo(3);
    // The same transfer request with another amount is a conflict, not a second transfer.
    transfer.put("amount", "60");
    assertThat(json(owner.send("POST", path("/transfers"), transfer)).get("code").asText())
        .isEqualTo("REQUEST_CONFLICT");
    transfer.put("toAccountId", cash);
    transfer.put("requestId", UUID.randomUUID().toString());
    assertThat(json(owner.send("POST", path("/transfers"), transfer)).get("code").asText())
        .isEqualTo("SAME_ACCOUNT");

    JsonNode statement = json(owner.get(path("/accounts/" + cash + "/statement")));
    assertThat(statement.get("openingBalance").asText()).isEqualTo("0.00");
    assertThat(statement.get("closingBalance").asText()).isEqualTo("100.00");
    JsonNode movements = statement.get("movements");
    assertThat(movements.size()).isEqualTo(2);
    assertThat(movements.get(0).get("kind").asText()).isEqualTo("TRANSFER_OUT");
    assertThat(movements.get(0).get("counterpartAccountId").asText()).isEqualTo(bankId);
    assertThat(movements.get(0).get("balanceAfter").asText()).isEqualTo("100.00");
    assertThat(movements.get(1).get("balanceAfter").asText()).isEqualTo("150.00");
    // A one-day statement; a period ending before it starts, or longer than a year, is refused.
    String tomorrow = LocalDate.parse(TODAY).plusDays(1).toString();
    var oneDay = owner.get(path("/accounts/" + cash + "/statement?from=" + TODAY + "&to=" + TODAY));
    assertThat(json(oneDay).get("movements").size()).isEqualTo(2);
    assertThat(
            owner
                .get(path("/accounts/" + cash + "/statement?from=" + tomorrow + "&to=" + TODAY))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            owner
                .get(path("/accounts/" + cash + "/statement?from=2024-01-01&to=" + TODAY))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            owner
                .get(path("/accounts/" + cash + "/statement?from=1999-01-01&to=1999-02-01"))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            owner
                .get(path("/accounts/" + cash + "/statement?from=2026-01-01&to=%2B999999999-12-31"))
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void theStatementPagesNewestFirstWithTheRightBalances() throws Exception {
    String cash = json(owner.get(path("/accounts"))).get(0).get("id").asText();
    // 52 adjustments of 1.00 on the same day: the pages split a run of equal days.
    for (int i = 1; i <= 52; i++)
      assertThat(
              owner
                  .send(
                      "POST",
                      path("/accounts/" + cash + "/adjustments"),
                      adjustment("1", "Ajuste " + i))
                  .statusCode())
          .isEqualTo(200);
    String statement = path("/accounts/" + cash + "/statement");
    JsonNode first = json(owner.get(statement));
    assertThat(first.get("movements")).hasSize(50);
    assertThat(first.get("movements").get(0).get("balanceAfter").asText()).isEqualTo("52.00");
    assertThat(first.get("closingBalance").asText()).isEqualTo("52.00");
    String cursor = first.get("nextCursor").asText();
    JsonNode second =
        json(
            owner.get(
                statement
                    + "?before="
                    + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8)));
    assertThat(second.get("movements")).hasSize(2);
    assertThat(second.get("nextCursor").isNull()).isTrue();
    // The running balance continues across the page boundary: 2.00 then 1.00.
    assertThat(second.get("movements").get(0).get("balanceAfter").asText()).isEqualTo("2.00");
    assertThat(second.get("movements").get(1).get("balanceAfter").asText()).isEqualTo("1.00");
    assertThat(owner.get(statement + "?before=junk").statusCode()).isEqualTo(400);
  }

  @Test
  void invalidMoneyDatesAndTooManyAccountsAreRefused() throws Exception {
    String cash = json(owner.get(path("/accounts"))).get(0).get("id").asText();
    for (String amount : List.of("1.234", "1e3", "0", "0.00", "abc", ""))
      assertThat(adjust(cash, adjustment(amount, "Ajuste qualquer")).statusCode())
          .as(amount)
          .isEqualTo(400);
    var future = adjustment("10", "Ajuste no futuro");
    future.put("occurredOn", LocalDate.parse(TODAY).plusDays(1).toString());
    assertThat(adjust(cash, future).statusCode()).isEqualTo(400);
    assertThat(adjust(cash, adjustment("10", "ok")).statusCode()).isEqualTo(400);
    assertThat(
            owner
                .send(
                    "POST",
                    path("/accounts"),
                    Map.of(
                        "requestId",
                        UUID.randomUUID().toString(),
                        "type",
                        "PAYMENT_PROVIDER",
                        "name",
                        "Falso"))
                .statusCode())
        .isEqualTo(400);
    for (int i = 0; i < 19; i++)
      assertThat(
              owner
                  .send(
                      "POST",
                      path("/accounts"),
                      Map.of(
                          "requestId",
                          UUID.randomUUID().toString(),
                          "type",
                          "BANK",
                          "name",
                          "Banco " + i))
                  .statusCode())
          .isEqualTo(200);
    var limit =
        owner.send(
            "POST",
            path("/accounts"),
            Map.of("requestId", UUID.randomUUID().toString(), "type", "CASH", "name", "Gaveta 2"));
    assertThat(json(limit).get("code").asText()).isEqualTo("ACCOUNT_LIMIT");
  }

  @Test
  void theDatabaseRefusesToChangeOrDeleteMovementsAndConcurrentTransfersStayWhole()
      throws Exception {
    String cash = json(owner.get(path("/accounts"))).get(0).get("id").asText();
    String bank =
        json(owner.send(
                "POST",
                path("/accounts"),
                Map.of("requestId", UUID.randomUUID().toString(), "type", "BANK", "name", "Banco")))
            .get("id")
            .asText();
    adjust(cash, adjustment("1000", "Saldo inicial"));
    assertThatThrownBy(() -> jdbc.update("UPDATE account_movement SET amount=1"))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM account_movement"))
        .hasMessageContaining("append-only");

    // The same transfer sent twice at once lands once; different transfers all land, whole.
    String repeated = UUID.randomUUID().toString();
    List<Callable<Integer>> calls = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      String request = i < 4 ? repeated : UUID.randomUUID().toString();
      calls.add(
          () ->
              owner
                  .send(
                      "POST",
                      path("/transfers"),
                      Map.of(
                          "requestId",
                          request,
                          "fromAccountId",
                          cash,
                          "toAccountId",
                          bank,
                          "amount",
                          "10",
                          "occurredOn",
                          TODAY))
                  .statusCode());
    }
    try (var pool = Executors.newFixedThreadPool(8)) {
      for (var result : pool.invokeAll(calls)) assertThat(result.get()).isEqualTo(200);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM account_movement WHERE kind='TRANSFER_OUT'", Long.class))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT transfer_id) FROM account_movement WHERE transfer_id IS NOT NULL",
                Long.class))
        .isEqualTo(5);
    JsonNode accounts = json(owner.get(path("/accounts")));
    assertThat(balance(accounts, cash)).isEqualByComparingTo("950.00");
    assertThat(balance(accounts, bank)).isEqualByComparingTo("50.00");
  }

  @Test
  void anotherOwnerAndAnEmployeeReachNothing() throws Exception {
    String cash = json(owner.get(path("/accounts"))).get(0).get("id").asText();
    activeIdentity("outra@example.test");
    Browser other = login("outra@example.test");
    String otherStore = create(other, "outra-loja");
    String otherCash =
        json(other.get("/api/tenants/" + otherStore + "/finance/accounts"))
            .get(0)
            .get("id")
            .asText();

    assertThat(other.get(path("/accounts")).statusCode()).isEqualTo(404);
    assertThat(other.get(path("/accounts/" + cash + "/statement")).statusCode()).isEqualTo(404);
    assertThat(
            other
                .send(
                    "POST",
                    path("/accounts/" + cash + "/adjustments"),
                    adjustment("10", "Tentativa"))
                .statusCode())
        .isEqualTo(404);
    // Her own store with an account of the first store: not found, nothing moves.
    assertThat(
            other
                .send(
                    "POST",
                    "/api/tenants/" + otherStore + "/finance/transfers",
                    Map.of(
                        "requestId",
                        UUID.randomUUID().toString(),
                        "fromAccountId",
                        otherCash,
                        "toAccountId",
                        cash,
                        "amount",
                        "10",
                        "occurredOn",
                        TODAY))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            other
                .send(
                    "POST",
                    "/api/tenants/" + otherStore + "/finance/accounts/" + cash + "/adjustments",
                    adjustment("10", "Tentativa"))
                .statusCode())
        .isEqualTo(404);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM account_movement", Long.class)).isZero();

    UUID employee = activeIdentity("caixa@example.test");
    jdbc.update(
        "INSERT INTO tenant_membership(tenant_id,user_id,role,created_at) VALUES (?,?,'STAFF',now())",
        UUID.fromString(store),
        employee);
    Browser worker = login("caixa@example.test");
    assertThat(worker.get(path("/accounts")).statusCode()).isEqualTo(404);
    assertThat(worker.get(path("/accounts/" + cash + "/statement")).statusCode()).isEqualTo(404);
    assertThat(
            worker
                .send(
                    "POST",
                    path("/accounts"),
                    Map.of(
                        "requestId", UUID.randomUUID().toString(), "type", "BANK", "name", "Banco"))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            worker
                .send(
                    "POST",
                    path("/transfers"),
                    Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "fromAccountId", cash,
                        "toAccountId", cash,
                        "amount", "1",
                        "occurredOn", TODAY))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            worker
                .send(
                    "POST",
                    path("/accounts/" + cash + "/adjustments"),
                    adjustment("10", "Tentativa"))
                .statusCode())
        .isEqualTo(404);
  }

  private HashMap<String, Object> adjustment(String amount, String reason) {
    return new HashMap<>(
        Map.of(
            "requestId",
            UUID.randomUUID().toString(),
            "amount",
            amount,
            "occurredOn",
            TODAY,
            "reason",
            reason));
  }

  private HttpResponse<String> adjust(String account, Map<String, Object> body) throws Exception {
    return owner.send("POST", path("/accounts/" + account + "/adjustments"), body);
  }

  private static java.math.BigDecimal balance(JsonNode accounts, String id) {
    for (JsonNode account : accounts)
      if (account.get("id").asText().equals(id))
        return new java.math.BigDecimal(account.get("balance").asText());
    throw new AssertionError("account " + id + " not listed");
  }

  private String path(String rest) {
    return "/api/tenants/" + store + "/finance" + rest;
  }

  private void connectMercadoPago(String tenant) {
    jdbc.update(
        "INSERT INTO payment_account(tenant_id,provider,provider_user_id,status,access_token,"
            + "refresh_token,access_expires_at,key_version,connected_at) VALUES "
            + "(?,'MERCADO_PAGO','987654','CONNECTED',?,?,now()+interval '1 day',1,now())",
        UUID.fromString(tenant),
        new byte[] {1, 2, 3},
        new byte[] {4, 5, 6});
  }

  private String create(Browser browser, String slug) throws Exception {
    var created =
        browser.send(
            "POST",
            "/api/tenants",
            Map.of("companyName", "Empresa " + slug, "storeName", "Loja " + slug, "slug", slug));
    assertThat(created.statusCode()).isEqualTo(201);
    return json(created).get("id").asText();
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

  private Browser login(String email) throws Exception {
    Browser browser = new Browser();
    browser.csrf();
    assertThat(
            browser
                .raw(
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
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asText();
    }

    HttpResponse<String> get(String path) throws Exception {
      return raw("GET", path, null, null);
    }

    HttpResponse<String> send(String method, String path, Object body) throws Exception {
      return raw(method, path, body == null ? null : JSON.writeValueAsString(body), csrfToken);
    }

    HttpResponse<String> raw(String method, String path, String body, String csrf)
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
