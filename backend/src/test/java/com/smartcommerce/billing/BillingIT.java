package com.smartcommerce.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
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
 * SC-031 over real HTTP and PostgreSQL with billing on (ADR-0017): the trial opens on first use
 * with the Profissional limits, companies and products stop at the plan's limits, an expired trial
 * or a charge past its grace blocks changes and takes the storefront offline without touching data,
 * and the assistant's quota follows the owner's plan.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "billing.enabled=true",
      "billing.stripe.secret-key=sk_test_synthetic",
      "billing.stripe.webhook-secret=whsec_synthetic",
      "orders.maintenance.enabled=false",
      "advisor.housekeeping.enabled=false"
    })
class BillingIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic billing secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID owner;
  UUID tenant;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    owner = activeIdentity("plano@example.test");
    tenant = store(owner, "loja-plano");
  }

  @Test
  void trialOpensWithProfissionalLimitsAndCompaniesStopAtThePlan() throws Exception {
    Browser a = login("plano@example.test");
    JsonNode account = JSON.readTree(a.get("/api/billing/account").body());
    assertThat(account.get("status").asText()).isEqualTo("TRIAL");
    assertThat(account.get("plan").isNull()).isTrue();
    assertThat(account.get("enforced").asBoolean()).isTrue();
    assertThat(account.get("limits").get("companies").asInt()).isEqualTo(2);
    assertThat(account.get("limits").get("productsPerStore").asInt()).isEqualTo(2000);
    assertThat(account.get("limits").get("monthlyQuestions").asInt()).isEqualTo(60);
    assertThat(account.get("companies").asLong()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT trial_ends_at - created_at = interval '14 days' FROM billing_account",
                Boolean.class))
        .isTrue();

    // The trial allows two companies: the second is created, the third is refused with a code.
    assertThat(a.send("POST", "/api/tenants", company("loja-plano-2")).statusCode()).isEqualTo(201);
    var third = a.send("POST", "/api/tenants", company("loja-plano-3"));
    assertThat(third.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(third.body()).get("code").asText()).isEqualTo("PLAN_LIMIT");
    assertThat(JSON.readTree(third.body()).get("message").asText()).contains("2 lojas");

    // Essencial: one company only, and the assistant has no questions.
    paid("ESSENCIAL");
    account = JSON.readTree(a.get("/api/billing/account").body());
    assertThat(account.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(account.get("plan").asText()).isEqualTo("ESSENCIAL");
    assertThat(account.get("limits").get("monthlyQuestions").asInt()).isZero();
    assertThat(a.send("POST", "/api/tenants", company("loja-plano-4")).statusCode()).isEqualTo(409);
    JsonNode advisor = JSON.readTree(a.get("/api/tenants/" + tenant + "/advisor").body());
    assertThat(advisor.get("monthlyQuota").asInt()).isZero();
    assertThat(advisor.get("remaining").asInt()).isZero();
  }

  @Test
  void productsStopAtThePlanLimitPerStore() throws Exception {
    Browser a = login("plano@example.test");
    assertThat(a.get("/api/billing/account").statusCode()).isEqualTo(200); // opens the account
    paid("ESSENCIAL");
    UUID store = jdbc.queryForObject("SELECT id FROM store WHERE tenant_id=?", UUID.class, tenant);
    // 149 products straight in the disposable database, then the API takes the 150th and stops.
    for (int i = 0; i < 149; i++)
      jdbc.update(
          "INSERT INTO catalog_product (id, tenant_id, store_id, name, sku, price, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'Produto', ?, 10.00, now(), now())",
          UUID.randomUUID(),
          tenant,
          store,
          "SKU-" + i);
    String products = "/api/tenants/" + tenant + "/products";
    assertThat(a.send("POST", products, product("Centésimo quinquagésimo", "SKU-149")).statusCode())
        .isEqualTo(201);
    var over = a.send("POST", products, product("Um a mais", "SKU-150"));
    assertThat(over.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(over.body()).get("code").asText()).isEqualTo("PLAN_LIMIT");
    assertThat(JSON.readTree(over.body()).get("message").asText()).contains("150 produtos");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_product", Long.class))
        .isEqualTo(150);
  }

  @Test
  void expiredTrialAndChargesPastGraceBlockChangesAndTheStorefrontWithoutLosingData()
      throws Exception {
    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id=?", tenant);
    Browser a = login("plano@example.test");
    String categories = "/api/tenants/" + tenant + "/categories";
    assertThat(a.send("POST", categories, Map.of("name", "Camisetas")).statusCode()).isEqualTo(201);
    assertThat(publicStore().statusCode()).isEqualTo(200);

    // Trial over: changes need a plan (402), reads still work, the storefront is offline.
    jdbc.update(
        "UPDATE billing_account SET trial_ends_at=now() - interval '1 minute', created_at=now() - interval '15 days'");
    var blocked = a.send("POST", categories, Map.of("name", "Bonés"));
    assertThat(blocked.statusCode()).isEqualTo(402);
    assertThat(JSON.readTree(blocked.body()).get("code").asText()).isEqualTo("PLAN_INACTIVE");
    assertThat(JSON.readTree(blocked.body()).get("message").asText()).contains("teste grátis");
    assertThat(JSON.readTree(a.get("/api/billing/account").body()).get("status").asText())
        .isEqualTo("TRIAL_EXPIRED");
    assertThat(JSON.readTree(a.get(categories).body())).hasSize(1);
    assertThat(publicStore().statusCode()).isEqualTo(404);
    assertThat(a.send("POST", "/api/tenants", company("loja-plano-2")).statusCode()).isEqualTo(402);

    // Paid again: everything is back, nothing was lost.
    paid("PROFISSIONAL");
    assertThat(a.send("POST", categories, Map.of("name", "Bonés")).statusCode()).isEqualTo(201);
    assertThat(publicStore().statusCode()).isEqualTo(200);

    // A failed charge keeps the store during the 7-day grace, then suspends it.
    jdbc.update(
        "UPDATE billing_account SET status='PAST_DUE', past_due_since=now() - interval '6 days'");
    assertThat(JSON.readTree(a.get("/api/billing/account").body()).get("status").asText())
        .isEqualTo("PAST_DUE");
    assertThat(
            a.send("PUT", categories + "/" + firstCategory(), Map.of("name", "Camisetas e regatas"))
                .statusCode())
        .isEqualTo(200);
    assertThat(publicStore().statusCode()).isEqualTo(200);
    jdbc.update("UPDATE billing_account SET past_due_since=now() - interval '8 days'");
    assertThat(JSON.readTree(a.get("/api/billing/account").body()).get("status").asText())
        .isEqualTo("SUSPENDED");
    assertThat(a.send("DELETE", categories + "/" + firstCategory(), null).statusCode())
        .isEqualTo(402);
    assertThat(publicStore().statusCode()).isEqualTo(404);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_category", Long.class))
        .isEqualTo(2);
  }

  private void paid(String plan) {
    jdbc.update(
        "UPDATE billing_account SET status='ACTIVE', plan=?, billing_cycle='MONTHLY', past_due_since=NULL,"
            + " current_period_end=now() + interval '30 days'",
        plan);
  }

  private UUID firstCategory() {
    return jdbc.queryForObject(
        "SELECT id FROM catalog_category ORDER BY name_key LIMIT 1", UUID.class);
  }

  private HttpResponse<String> publicStore() throws Exception {
    return new Browser().get("/api/public/stores/loja-plano");
  }

  private static Map<String, Object> company(String slug) {
    return Map.of("companyName", "Empresa " + slug, "storeName", "Loja " + slug, "slug", slug);
  }

  private static Map<String, Object> product(String name, String sku) {
    Map<String, Object> result = new HashMap<>();
    result.put("name", name);
    result.put("sku", sku);
    result.put("price", "49.90");
    result.put("cost", null);
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

  private UUID store(UUID user, String slug) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        id);
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'OWNER', now())",
        id,
        user);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, created_at) VALUES (?, ?, 'Synthetic store', ?, now())",
        UUID.randomUUID(),
        id,
        slug);
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
