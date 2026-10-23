package com.smartcommerce.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.finance.repositories.FinanceLedgerRepository;
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
import java.util.ArrayList;
import java.util.List;
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
 * SC-024 with known data: each rule fires at its limit and not below it, alerts disappear with
 * their cause, the most severe come first, no buyer data appears and A/B isolation holds. The
 * Mercado Pago application is configured (never called) so the connection rules can be exercised.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orders.maintenance.enabled=false",
      "payments.token-refresh.enabled=false",
      "payments.events.enabled=false",
      "payments.reconciliation.enabled=false",
      "payments.mercado-pago.return-origin=http://127.0.0.1:3001",
      "payments.mercado-pago.client-id=1234567890",
      "payments.mercado-pago.client-secret=synthetic-client-secret-for-tests",
      "payments.mercado-pago.redirect-uri=http://127.0.0.1:3000/api/payments/mercado-pago/callback",
      "payments.token-encryption-key=c3ludGhldGljLTMyLWJ5dGUtdGVzdC1rZXktMDAwMDA="
    })
class AlertIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic alerts secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired FinanceLedgerService ledger;
  @Autowired FinanceLedgerRepository entries;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    tenantA = store(activeIdentity("alertas-a@example.test"), "alertas-a");
    tenantB = store(activeIdentity("alertas-b@example.test"), "alertas-b");
  }

  @Test
  void emptyStoreOnlyAsksToConnectPayments() throws Exception {
    Browser a = login("alertas-a@example.test");
    var response = a.get(path(tenantA));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("marginPeriod").asString()).isEqualTo("THIS_MONTH");
    assertThat(codes(body)).containsExactly("PAYMENTS_NOT_CONNECTED");
    assertThat(body.get("alerts").get(0).get("severity").asString()).isEqualTo("ATTENTION");
    assertThat(body.get("alerts").get(0).get("target").asString()).isEqualTo("PAYMENTS");
  }

  @Test
  void rulesFireAtTheirLimitsAndComeOrderedBySeverity() throws Exception {
    variant("Tênis", 5, 60); // available 5 = limit: low stock (and never sold: without sales)
    variant("Bolsa", 6, 1); // above the limit and too new to be stale: no alert
    UUID soon = TestOrders.create(jdbc, tenantA);
    expiresIn(soon, Duration.ofMinutes(9)); // inside the 10-minute window
    UUID later = TestOrders.create(jdbc, tenantA);
    expiresIn(later, Duration.ofMinutes(11)); // outside
    UUID gone = TestOrders.create(jdbc, tenantA);
    expiresIn(gone, Duration.ofMinutes(-1)); // already past its deadline: not "expiring"
    connected(tenantA, "now()-interval '1 minute'"); // access token past expiry: EXPIRED

    Instant now = Instant.now();
    UUID noCost = TestOrders.create(jdbc, tenantA);
    ledger.recordSale(tenantA, noCost, new BigDecimal("80.00"), BigDecimal.ZERO, true, now);

    Browser a = login("alertas-a@example.test");
    var response = a.get(path(tenantA));
    JsonNode body = JSON.readTree(response.body());
    assertThat(codes(body))
        .containsExactly(
            "PAYMENTS_EXPIRED",
            "COST_MISSING",
            "ORDERS_EXPIRING",
            "LOW_STOCK",
            "FEES_UNAVAILABLE",
            "WITHOUT_SALES");
    assertThat(alert(body, "ORDERS_EXPIRING").get("count").asLong()).isEqualTo(1);
    assertThat(alert(body, "LOW_STOCK").get("count").asLong()).isEqualTo(1);
    assertThat(alert(body, "COST_MISSING").get("count").asLong()).isEqualTo(1);
    assertThat(alert(body, "FEES_UNAVAILABLE").get("count").asLong()).isEqualTo(1);
    assertThat(alert(body, "WITHOUT_SALES").get("count").asLong()).isEqualTo(1);
    assertThat(alert(body, "PAYMENTS_EXPIRED").get("severity").asString()).isEqualTo("CRITICAL");
    // No buyer data, SKU or cost ever leaves in an alert.
    assertThat(response.body())
        .doesNotContain("buyer@example.test", "Comprador", "11999990000", "SKU-", "Tênis");

    // Alerts disappear with their cause.
    jdbc.update("UPDATE inventory_balance SET quantity=6 WHERE tenant_id=?", tenantA);
    expiresIn(soon, Duration.ofMinutes(25));
    jdbc.update("DELETE FROM payment_account WHERE tenant_id=?", tenantA);
    connected(tenantA, "now()+interval '100 days'");
    assertThat(codes(JSON.readTree(a.get(path(tenantA)).body())))
        .containsExactly("COST_MISSING", "FEES_UNAVAILABLE", "WITHOUT_SALES");
  }

  @Test
  void negativeMarginOnlyWhenTheMonthIsCompleteAndRevokedConnectionIsCritical() throws Exception {
    Instant now = Instant.now();
    UUID order = TestOrders.create(jdbc, tenantA);
    ledger.recordSale(
        tenantA, order, new BigDecimal("100.00"), new BigDecimal("120.00"), false, now);
    revoked(tenantA);

    Browser a = login("alertas-a@example.test");
    // Fee unknown: the margin is partial, so no "negative margin" is claimed.
    JsonNode partial = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(codes(partial)).containsExactly("PAYMENTS_REVOKED", "FEES_UNAVAILABLE");

    entries.append(tenantA, "fee:" + order, "FEE", new BigDecimal("-5.00"), now, order, false, now);
    JsonNode complete = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(codes(complete)).containsExactly("PAYMENTS_REVOKED", "MARGIN_NEGATIVE");
    JsonNode margin = alert(complete, "MARGIN_NEGATIVE");
    assertThat(margin.get("amount").asString()).isEqualTo("-25.00");
    assertThat(margin.get("target").asString()).isEqualTo("FINANCE");
  }

  @Test
  void anotherOwnerAndAnonymousCallersSeeNothing() throws Exception {
    variant("Só de A", 1, 60);
    Browser b = login("alertas-b@example.test");
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(b.get("/api/tenants/nao-e-uuid/alerts").statusCode()).isEqualTo(404);
    assertThat(codes(JSON.readTree(b.get(path(tenantB)).body())))
        .containsExactly("PAYMENTS_NOT_CONNECTED");
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);
  }

  private static List<String> codes(JsonNode body) {
    List<String> codes = new ArrayList<>();
    for (JsonNode alert : body.get("alerts")) codes.add(alert.get("code").asString());
    return codes;
  }

  private static JsonNode alert(JsonNode body, String code) {
    for (JsonNode alert : body.get("alerts"))
      if (code.equals(alert.get("code").asString())) return alert;
    throw new AssertionError("Missing alert " + code);
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/alerts";
  }

  private void expiresIn(UUID order, Duration delta) {
    jdbc.update(
        "UPDATE sales_order SET created_at=now()+make_interval(secs=>?)-interval '30 minutes',"
            + " reservation_expires_at=now()+make_interval(secs=>?) WHERE id=?",
        delta.toSeconds(),
        delta.toSeconds(),
        order);
  }

  private void connected(UUID tenant, String accessExpiresAt) {
    jdbc.update(
        "INSERT INTO payment_account(tenant_id,provider,provider_user_id,status,access_token,"
            + "refresh_token,access_expires_at,key_version,connected_at) VALUES "
            + "(?,'MERCADO_PAGO','987654','CONNECTED',?,?,"
            + accessExpiresAt
            + ",1,now())",
        tenant,
        new byte[] {1, 2, 3},
        new byte[] {4, 5, 6});
  }

  private void revoked(UUID tenant) {
    jdbc.update(
        "INSERT INTO payment_account(tenant_id,provider,provider_user_id,status,connected_at,"
            + "closed_at) VALUES (?,'MERCADO_PAGO','987654','REVOKED',now(),now())",
        tenant);
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
