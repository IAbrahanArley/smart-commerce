package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.finance.domain.ExpenseInput;
import com.smartcommerce.finance.services.ExpenseService;
import com.smartcommerce.finance.services.FinanceLedgerService;
import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.smartcommerce.orders.TestOrders;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
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
 * SC-021 against a scenario computed by hand (ADR-0015 F1–F5): competence in the São Paulo calendar
 * (a sale at 23:30 on the last day stays in that month), refunds counted when confirmed even in a
 * later month, COGS reversal only with restock, incomplete costs and unknown fees marking the
 * margin as partial, receivable and cash by settlement, and A/B isolation.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class FinanceReportIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic finance report secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired FinanceLedgerService ledger;
  @Autowired ExpenseService expenses;
  UUID ownerA;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = activeIdentity("relatorio-a@example.test");
    UUID ownerB = activeIdentity("relatorio-b@example.test");
    tenantA = store(ownerA, "relatorio-a");
    tenantB = store(ownerB, "relatorio-b");
  }

  @Test
  void reportMatchesAHandComputedScenario() throws Exception {
    // September 2026 (by hand): sales 100 + 50 + 30 = 180; refund of an August sale 80;
    // COGS 40 + 20 + 10 (last one incomplete) = 70; variable expenses 15 (5 cancelled);
    // margin 180 − 80 − 0 − 70 − 15 = 15, i.e. 15.00% of 100; fixed 500; ticket 180 / 3 = 60.
    sale("100.00", "40.00", false, at(2026, 9, 5, 12, 0));
    UUID o2 = sale("50.00", "20.00", false, at(2026, 9, 20, 9, 0));
    UUID o3 = sale("80.00", "30.00", false, at(2026, 8, 28, 15, 0));
    sale("30.00", "10.00", true, at(2026, 9, 30, 23, 30)); // 02:30 UTC on Oct 1
    ledger.recordRefund(tenantA, o3, new BigDecimal("80.00"), false, at(2026, 9, 3, 10, 0));
    ledger.recordRefund(tenantA, o2, new BigDecimal("50.00"), true, at(2026, 10, 2, 10, 0));
    expense("VARIABLE", "15.00", "2026-09-08", "2026-09-10");
    expense("FIXED", "500.00", "2026-09-01", null);
    var cancelled = expense("VARIABLE", "5.00", "2026-09-15", null);
    expenses.cancel(ownerA, tenantA, cancelled, "Lançada em dobro");

    Browser a = login("relatorio-a@example.test");
    var response = a.get(path(tenantA) + "?month=2026-09");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode september = JSON.readTree(response.body());
    assertThat(september.get("period").get("firstDay").asString()).isEqualTo("2026-09-01");
    assertThat(september.get("period").get("lastDay").asString()).isEqualTo("2026-09-30");
    assertThat(september.get("grossRevenue").asString()).isEqualTo("180.00");
    assertThat(september.get("refunds").asString()).isEqualTo("80.00");
    assertThat(september.get("fees").asString()).isEqualTo("0.00");
    assertThat(september.get("netRevenue").asString()).isEqualTo("100.00");
    assertThat(september.get("cogs").asString()).isEqualTo("70.00");
    assertThat(september.get("variableExpenses").asString()).isEqualTo("15.00");
    assertThat(september.get("contributionMargin").asString()).isEqualTo("15.00");
    assertThat(september.get("marginPercent").asString()).isEqualTo("15.00");
    assertThat(september.get("fixedExpenses").asString()).isEqualTo("500.00");
    assertThat(september.get("paidOrders").asLong()).isEqualTo(3);
    assertThat(september.get("averageTicket").asString()).isEqualTo("60.00");
    // Partial, never a definitive profit: three sales without fees, one order without full cost.
    assertThat(september.get("complete").asBoolean()).isFalse();
    assertThat(september.get("missing").toString())
        .contains("\"FEES_UNAVAILABLE\"", "\"count\":3", "\"COST_MISSING\"", "\"count\":1");
    // Cash moved in September: the paid expense only (no release from the provider yet).
    assertThat(september.get("cashMovement").asString()).isEqualTo("-15.00");
    // Receivable at the end of September: 260 sold − 80 refunded, all before unknown fees.
    assertThat(september.get("receivable").get("amount").asString()).isEqualTo("180.00");
    assertThat(september.get("receivable").get("feesUnknown").asLong()).isEqualTo(4);

    // October: the refund of a September sale counts now, and its COGS comes back (restock).
    JsonNode october = JSON.readTree(a.get(path(tenantA) + "?month=2026-10").body());
    assertThat(october.get("grossRevenue").asString()).isEqualTo("0.00");
    assertThat(october.get("refunds").asString()).isEqualTo("50.00");
    assertThat(october.get("cogs").asString()).isEqualTo("-20.00");
    assertThat(october.get("contributionMargin").asString()).isEqualTo("-30.00");
    assertThat(october.get("marginPercent").isNull()).isTrue();
    assertThat(october.get("averageTicket").isNull()).isTrue();
    assertThat(october.get("complete").asBoolean()).isTrue(); // nothing sold, nothing missing

    // August keeps its own sale and cost; the refund in September did not rewrite it.
    JsonNode august = JSON.readTree(a.get(path(tenantA) + "?month=2026-08").body());
    assertThat(august.get("grossRevenue").asString()).isEqualTo("80.00");
    assertThat(august.get("refunds").asString()).isEqualTo("0.00");
    assertThat(august.get("cogs").asString()).isEqualTo("30.00");
  }

  @Test
  void periodsValidationSessionAndOtherStores() throws Exception {
    sale("10.00", "1.00", false, Instant.now().minusSeconds(60));
    Browser a = login("relatorio-a@example.test");
    for (String period : new String[] {"TODAY", "LAST_7_DAYS", "LAST_30_DAYS", "THIS_MONTH"}) {
      JsonNode report = JSON.readTree(a.get(path(tenantA) + "?period=" + period).body());
      assertThat(report.get("paidOrders").asLong()).as(period).isEqualTo(1);
    }
    assertThat(
            JSON.readTree(a.get(path(tenantA) + "?period=LAST_MONTH").body())
                .get("paidOrders")
                .asLong())
        .isZero();
    for (String query : new String[] {"?period=FOREVER", "?month=2026-13", "?month=2026-1"})
      assertThat(a.get(path(tenantA) + query).statusCode()).as(query).isEqualTo(400);
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);
    Browser b = login("relatorio-b@example.test");
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(
            JSON.readTree(b.get(path(tenantB) + "?period=THIS_MONTH").body())
                .get("paidOrders")
                .asLong())
        .isZero();
  }

  private UUID sale(String total, String cost, boolean incomplete, Instant paidAt) {
    UUID order = TestOrders.create(jdbc, tenantA);
    ledger.recordSale(
        tenantA, order, new BigDecimal(total), new BigDecimal(cost), incomplete, paidAt);
    return order;
  }

  private UUID expense(String kind, String amount, String competence, String paid) {
    return expenses
        .create(
            ownerA,
            tenantA,
            ExpenseInput.of(
                UUID.randomUUID().toString(),
                "OTHER",
                kind,
                "Despesa sintética",
                amount,
                competence,
                paid,
                expenses.today()))
        .id();
  }

  private static Instant at(int year, int month, int day, int hour, int minute) {
    return LocalDateTime.of(year, month, day, hour, minute).atZone(SP).toInstant();
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/finance";
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
