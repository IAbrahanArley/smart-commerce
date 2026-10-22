package com.smartcommerce.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.smartcommerce.finance.repositories.FinanceReportRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SC-022 with known stock and sales: the dashboard's figures are the financial report's, sales per
 * day cover every day of the period, low stock counts reservations in force, items without turnover
 * skip recent sales and new products, and A/B isolation holds.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class DashboardIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic dashboard secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired FinanceLedgerService ledger;
  @MockitoSpyBean FinanceReportRepository reports;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    tenantA = store(activeIdentity("painel-a@example.test"), "painel-a");
    tenantB = store(activeIdentity("painel-b@example.test"), "painel-b");
  }

  @Test
  void dashboardShowsTheReportFiguresSalesPerDayAndStockLists() throws Exception {
    UUID low = variant("Tênis", 3, 60);
    UUID reserved = variant("Bolsa", 7, 60);
    reserve(reserved, 3); // available 4: low stock counts reservations in force
    UUID stale = variant("Cinto", 50, 60); // never sold, created 60 days ago
    UUID sold = variant("Boné", 20, 60);
    saleMovement(sold, 5); // sold 5 days ago: not stale
    variant("Lenço", 20, 1); // created yesterday: too new to be stale
    UUID oldSale = variant("Meia", 30, 90);
    saleMovement(oldSale, 45); // last sale 45 days ago: stale

    Instant now = Instant.now();
    ledger.recordSale(
        tenantA,
        TestOrders.create(jdbc, tenantA),
        new BigDecimal("100.00"),
        new BigDecimal("40.00"),
        false,
        now.minusSeconds(60));
    ledger.recordSale(
        tenantA,
        TestOrders.create(jdbc, tenantA),
        new BigDecimal("50.00"),
        new BigDecimal("10.00"),
        true,
        now.minus(Duration.ofDays(1)));

    Browser a = login("painel-a@example.test");
    var response = a.get("/api/tenants/" + tenantA + "/dashboard?period=LAST_7_DAYS");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode dashboard = JSON.readTree(response.body());
    JsonNode report =
        JSON.readTree(a.get("/api/tenants/" + tenantA + "/finance?period=LAST_7_DAYS").body());

    // Same numbers as the financial report (one source of truth).
    assertThat(dashboard.get("paidOrders").asLong())
        .isEqualTo(2)
        .isEqualTo(report.get("paidOrders").asLong());
    assertThat(dashboard.get("revenue").asString())
        .isEqualTo("150.00")
        .isEqualTo(report.get("grossRevenue").asString());
    assertThat(dashboard.get("averageTicket").asString()).isEqualTo("75.00");
    assertThat(dashboard.get("contributionMargin").asString())
        .isEqualTo(report.get("contributionMargin").asString());
    assertThat(dashboard.get("marginComplete").asBoolean())
        .isFalse(); // fees unknown, one cost missing

    // Seven days, every one present, today's sale on today's row (São Paulo calendar).
    JsonNode days = dashboard.get("salesByDay");
    assertThat(days).hasSize(7);
    String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
    assertThat(days.get(6).get("day").asString()).isEqualTo(today);
    assertThat(days.get(6).get("revenue").asString()).isEqualTo("100.00");
    long orders = 0;
    for (JsonNode day : days) orders += day.get("orders").asLong();
    assertThat(orders).isEqualTo(2);

    assertThat(dashboard.get("lowStockThreshold").asInt()).isEqualTo(5);
    assertThat(dashboard.get("lowStockCount").asLong()).isEqualTo(2);
    assertThat(ids(dashboard.get("lowStock"))).containsExactly(low.toString(), reserved.toString());
    assertThat(dashboard.get("lowStock").get(1).get("available").asInt()).isEqualTo(4);
    assertThat(dashboard.get("staleAfterDays").asInt()).isEqualTo(30);
    assertThat(dashboard.get("withoutSalesCount").asLong())
        .isEqualTo(4); // includes the low ones never sold
    assertThat(ids(dashboard.get("withoutSales")))
        .contains(stale.toString(), oldSale.toString())
        .doesNotContain(sold.toString());
    assertThat(response.body()).doesNotContain("SKU-", "cost");
  }

  @Test
  void ticketWithoutSalesValidationSessionAndOtherStores() throws Exception {
    Browser a = login("painel-a@example.test");
    JsonNode empty =
        JSON.readTree(a.get("/api/tenants/" + tenantA + "/dashboard?period=TODAY").body());
    assertThat(empty.get("paidOrders").asLong()).isZero();
    assertThat(empty.get("averageTicket").isNull()).isTrue(); // never divides by zero
    assertThat(empty.get("salesByDay")).hasSize(1);
    assertThat(a.get("/api/tenants/" + tenantA + "/dashboard?period=FOREVER").statusCode())
        .isEqualTo(400);
    assertThat(new Browser().get("/api/tenants/" + tenantA + "/dashboard").statusCode())
        .isEqualTo(401);

    variant("Só de A", 1, 60);
    Browser b = login("painel-b@example.test");
    assertThat(b.get("/api/tenants/" + tenantA + "/dashboard").statusCode()).isEqualTo(404);
    JsonNode own = JSON.readTree(b.get("/api/tenants/" + tenantB + "/dashboard").body());
    assertThat(own.get("lowStockCount").asLong()).isZero();
    assertThat(own.get("lowStock")).isEmpty();
  }

  @Test
  void concurrentSaleDoesNotSplitSummaryAndDailySnapshot() throws Exception {
    UUID order = TestOrders.create(jdbc, tenantA);
    Instant soldAt = Instant.now();
    doAnswer(
            invocation -> {
              // Commit a sale on another connection after the summary, before the daily query.
              try (var executor = Executors.newSingleThreadExecutor()) {
                executor
                    .submit(
                        () ->
                            ledger.recordSale(
                                tenantA,
                                order,
                                new BigDecimal("100.00"),
                                new BigDecimal("40.00"),
                                false,
                                soldAt))
                    .get(10, TimeUnit.SECONDS);
              }
              return invocation.callRealMethod();
            })
        .when(reports)
        .salesByDay(eq(tenantA), any(Instant.class), any(Instant.class));

    Browser a = login("painel-a@example.test");
    var response = a.get("/api/tenants/" + tenantA + "/dashboard?period=TODAY");
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode dashboard = JSON.readTree(response.body());
    assertThat(dashboard.get("revenue").asString()).isEqualTo("0.00");
    assertThat(dashboard.get("salesByDay").get(0).get("revenue").asString()).isEqualTo("0.00");
    // A following request observes the committed sale.
    JsonNode report =
        JSON.readTree(a.get("/api/tenants/" + tenantA + "/finance?period=TODAY").body());
    assertThat(report.get("grossRevenue").asString()).isEqualTo("100.00");
  }

  private static List<String> ids(JsonNode items) {
    List<String> ids = new ArrayList<>();
    for (JsonNode item : items) ids.add(item.get("variantId").asString());
    return ids;
  }

  private UUID variant(String name, int stock, int createdDaysAgo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind) SELECT ?,?,id,?,?,10.00,'BRL',now()-make_interval(days=>?),now(),true,'SIMPLE' FROM store WHERE tenant_id=?",
        id,
        tenantA,
        name,
        "SKU-" + id.toString().substring(0, 8),
        createdDaysAgo,
        tenantA);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,10.00,NULL,'BRL','[]',?)",
        id,
        tenantA,
        id,
        "SKU-" + id.toString().substring(0, 8),
        id.toString());
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,?)",
        id,
        tenantA,
        stock);
    return id;
  }

  private void reserve(UUID variant, int quantity) {
    UUID order = TestOrders.create(jdbc, tenantA);
    jdbc.update(
        "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at) VALUES (?,?,?,?,?,'ACTIVE',now()+interval '20 minutes',now())",
        UUID.randomUUID(),
        tenantA,
        variant,
        order,
        quantity);
  }

  private void saleMovement(UUID variant, int daysAgo) {
    UUID order = TestOrders.create(jdbc, tenantA);
    jdbc.update(
        """
        INSERT INTO inventory_movement(id,tenant_id,product_id,actor_id,actor_kind,request_id,type,
          delta,balance_after,reason,created_at,order_id)
        VALUES (?,?,?,NULL,'SYSTEM',?,'SALE',-1,1,'Venda sintética',now()-make_interval(days=>?),?)
        """,
        UUID.randomUUID(),
        tenantA,
        variant,
        UUID.randomUUID(),
        daysAgo,
        order);
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
