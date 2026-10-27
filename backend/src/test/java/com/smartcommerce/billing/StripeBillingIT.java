package com.smartcommerce.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.stripe.net.Webhook;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * SC-032 over real HTTP and PostgreSQL against a fake Stripe API (ADR-0017 B3–B6): Checkout opens
 * for the chosen price and creates the customer once, a second subscription is refused, only a
 * signed webhook changes the account and then from the subscription the API reports, redeliveries
 * are not processed twice, the grace counts from the first failed charge, and a Stripe failure
 * leaves the event for Stripe's retry.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "billing.enabled=true",
      "billing.stripe.secret-key=sk_test_synthetic",
      "billing.stripe.webhook-secret=" + StripeBillingIT.WEBHOOK_SECRET,
      "orders.maintenance.enabled=false",
      "advisor.housekeeping.enabled=false"
    })
class StripeBillingIT {
  static final String WEBHOOK_SECRET = "whsec_synthetic_for_tests";

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic stripe billing secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final long PERIOD_END = 1_830_000_000L;
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  // The subscription the fake API reports: status and price lookup key, or null for none.
  private static final AtomicReference<String[]> SUBSCRIPTION = new AtomicReference<>();
  private static final AtomicBoolean FAILING = new AtomicBoolean();
  private static final HttpServer STRIPE = stripe();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID owner;

  @DynamicPropertySource
  static void stripeApi(DynamicPropertyRegistry registry) {
    registry.add(
        "billing.stripe.api-base", () -> "http://127.0.0.1:" + STRIPE.getAddress().getPort());
  }

  private static HttpServer stripe() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/v1/",
          exchange -> {
            String body =
                URLDecoder.decode(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            String query =
                exchange.getRequestURI().getRawQuery() == null
                    ? ""
                    : URLDecoder.decode(
                        exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            REQUESTS.add(exchange.getRequestMethod() + " " + path + " " + query + " " + body);
            String reply = FAILING.get() ? null : reply(path, query);
            byte[] bytes =
                (reply == null
                        ? "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}"
                        : reply)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply == null ? 500 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static String reply(String path, String query) {
    return switch (path) {
      case "/v1/customers" -> "{\"id\":\"cus_synthetic\",\"object\":\"customer\"}";
      case "/v1/prices" -> {
        String key = query.replaceAll(".*lookup_keys\\[0]=([a-z_]+).*", "$1");
        yield "{\"object\":\"list\",\"url\":\"/v1/prices\",\"has_more\":false,\"data\":[{\"id\":\"price_"
            + key
            + "\",\"object\":\"price\",\"lookup_key\":\""
            + key
            + "\"}]}";
      }
      case "/v1/checkout/sessions" ->
          "{\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_1\"}";
      case "/v1/billing_portal/sessions" ->
          "{\"id\":\"bps_1\",\"object\":\"billing_portal.session\",\"url\":\"https://billing.stripe.com/p/session/test_1\"}";
      case "/v1/subscriptions" -> {
        String[] current = SUBSCRIPTION.get();
        yield "{\"object\":\"list\",\"url\":\"/v1/subscriptions\",\"has_more\":false,\"data\":["
            + (current == null
                ? ""
                : "{\"id\":\"sub_synthetic\",\"object\":\"subscription\",\"status\":\""
                    + current[0]
                    + "\",\"created\":1790000000,\"cancel_at_period_end\":false,\"customer\":\"cus_synthetic\","
                    + "\"items\":{\"object\":\"list\",\"data\":[{\"id\":\"si_1\",\"object\":\"subscription_item\","
                    + "\"current_period_end\":"
                    + PERIOD_END
                    + ",\"price\":{\"id\":\"price_x\",\"object\":\"price\",\"lookup_key\":\""
                    + current[1]
                    + "\"}}]}}")
            + "]}";
      }
      default -> null;
    };
  }

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, billing_event CASCADE");
    REQUESTS.clear();
    SUBSCRIPTION.set(null);
    FAILING.set(false);
    owner = activeIdentity("assinante@example.test");
  }

  @Test
  void checkoutOpensForTheChosenPriceAndASignedWebhookActivatesThePlan() throws Exception {
    Browser a = login("assinante@example.test");
    var checkout =
        a.send("POST", "/api/billing/checkout", Map.of("plan", "PROFISSIONAL", "cycle", "ANNUAL"));
    assertThat(checkout.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(checkout.body()).get("url").asText())
        .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
    assertThat(REQUESTS)
        .anyMatch(r -> r.startsWith("POST /v1/customers") && r.contains("assinante@example.test"))
        .anyMatch(r -> r.startsWith("GET /v1/prices") && r.contains("profissional_anual"))
        .anyMatch(
            r ->
                r.startsWith("POST /v1/checkout/sessions")
                    && r.contains("price_profissional_anual")
                    && r.contains("mode=subscription")
                    && r.contains("client_reference_id=" + owner)
                    && r.contains("/conta/plano?checkout=sucesso"));
    assertThat(
            jdbc.queryForObject(
                "SELECT stripe_customer_id FROM billing_account WHERE user_id=?",
                String.class,
                owner))
        .isEqualTo("cus_synthetic");

    // The browser's return changes nothing; unsigned or forged events are refused.
    assertThat(status()).isEqualTo("TRIAL");
    SUBSCRIPTION.set(new String[] {"active", "profissional_anual"});
    String event = event("evt_1", "customer.subscription.created");
    assertThat(webhook(event, null).statusCode()).isEqualTo(400);
    assertThat(webhook(event, signature(event, "whsec_forged")).statusCode()).isEqualTo(400);
    assertThat(status()).isEqualTo("TRIAL");

    // A signed event: the account follows the subscription the API reports.
    assertThat(webhook(event, signature(event, WEBHOOK_SECRET)).statusCode()).isEqualTo(200);
    var account = jdbc.queryForMap("SELECT * FROM billing_account WHERE user_id=?", owner);
    assertThat(account.get("status")).isEqualTo("ACTIVE");
    assertThat(account.get("plan")).isEqualTo("PROFISSIONAL");
    assertThat(account.get("billing_cycle")).isEqualTo("ANNUAL");
    assertThat(account.get("stripe_subscription_id")).isEqualTo("sub_synthetic");
    assertThat(
            jdbc.queryForObject(
                "SELECT extract(epoch FROM current_period_end)::bigint FROM billing_account",
                Long.class))
        .isEqualTo(PERIOD_END);

    // A redelivery is acknowledged without asking Stripe again.
    long listed = REQUESTS.stream().filter(r -> r.startsWith("GET /v1/subscriptions")).count();
    assertThat(webhook(event, signature(event, WEBHOOK_SECRET)).statusCode()).isEqualTo(200);
    assertThat(REQUESTS.stream().filter(r -> r.startsWith("GET /v1/subscriptions")).count())
        .isEqualTo(listed);

    // Subscribed: a second Checkout is refused, plan changes go through the portal.
    var again =
        a.send("POST", "/api/billing/checkout", Map.of("plan", "ESCALA", "cycle", "MONTHLY"));
    assertThat(again.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(again.body()).get("code").asText()).isEqualTo("SUBSCRIPTION_EXISTS");
    var portal = a.send("POST", "/api/billing/portal", null);
    assertThat(portal.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(portal.body()).get("url").asText())
        .isEqualTo("https://billing.stripe.com/p/session/test_1");
    assertThat(REQUESTS.stream().filter(r -> r.startsWith("POST /v1/customers")).count())
        .isEqualTo(1);
  }

  @Test
  void graceCountsFromTheFirstFailedChargeAndCancellationEndsThePlan() throws Exception {
    Browser a = login("assinante@example.test");
    assertThat(a.send("POST", "/api/billing/portal", null).statusCode()).isEqualTo(409);
    a.send("POST", "/api/billing/checkout", Map.of("plan", "ESSENCIAL", "cycle", "MONTHLY"));

    SUBSCRIPTION.set(new String[] {"past_due", "essencial_mensal"});
    deliver("evt_failed_1", "invoice.payment_failed");
    assertThat(status()).isEqualTo("PAST_DUE");
    jdbc.update("UPDATE billing_account SET past_due_since=now() - interval '3 days'");
    deliver("evt_failed_2", "invoice.payment_failed");
    assertThat(
            jdbc.queryForObject(
                "SELECT past_due_since < now() - interval '2 days' FROM billing_account",
                Boolean.class))
        .isTrue();

    SUBSCRIPTION.set(new String[] {"active", "essencial_mensal"});
    deliver("evt_paid", "invoice.paid");
    assertThat(status()).isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT past_due_since IS NULL FROM billing_account", Boolean.class))
        .isTrue();

    SUBSCRIPTION.set(new String[] {"canceled", "essencial_mensal"});
    deliver("evt_deleted", "customer.subscription.deleted");
    assertThat(status()).isEqualTo("CANCELED");
    assertThat(JSON.readTree(a.get("/api/billing/account").body()).get("status").asText())
        .isEqualTo("CANCELED");
    // After a cancellation the owner may subscribe again.
    assertThat(
            a.send("POST", "/api/billing/checkout", Map.of("plan", "ESCALA", "cycle", "ANNUAL"))
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void aStripeFailureLeavesTheEventForTheRetryAndOversizedBodiesAreRefused() throws Exception {
    Browser a = login("assinante@example.test");
    a.send("POST", "/api/billing/checkout", Map.of("plan", "ESCALA", "cycle", "SEMIANNUAL"));
    SUBSCRIPTION.set(new String[] {"active", "escala_semestral"});
    String event = event("evt_retry", "customer.subscription.updated");

    FAILING.set(true);
    assertThat(webhook(event, signature(event, WEBHOOK_SECRET)).statusCode()).isEqualTo(503);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event", Long.class)).isZero();
    assertThat(status()).isEqualTo("TRIAL");

    FAILING.set(false);
    assertThat(webhook(event, signature(event, WEBHOOK_SECRET)).statusCode()).isEqualTo(200);
    assertThat(status()).isEqualTo("ACTIVE");
    assertThat(jdbc.queryForObject("SELECT billing_cycle FROM billing_account", String.class))
        .isEqualTo("SEMIANNUAL");

    // Unknown event types are acknowledged and ignored; oversized bodies never reach the service.
    assertThat(webhook(event("evt_other", "charge.refunded"), null).statusCode()).isEqualTo(400);
    String other = event("evt_other", "charge.refunded");
    assertThat(webhook(other, signature(other, WEBHOOK_SECRET)).statusCode()).isEqualTo(200);
    String huge = "{\"pad\":\"" + "x".repeat(70 * 1024) + "\"}";
    assertThat(webhook(huge, signature(huge, WEBHOOK_SECRET)).statusCode()).isEqualTo(413);
  }

  private void deliver(String id, String type) throws Exception {
    String event = event(id, type);
    assertThat(webhook(event, signature(event, WEBHOOK_SECRET)).statusCode()).isEqualTo(200);
  }

  private String status() {
    return jdbc.queryForObject(
        "SELECT status FROM billing_account WHERE user_id=?", String.class, owner);
  }

  private static String event(String id, String type) {
    return "{\"id\":\""
        + id
        + "\",\"object\":\"event\",\"type\":\""
        + type
        + "\",\"data\":{\"object\":{\"id\":\"obj_1\",\"customer\":\"cus_synthetic\"}}}";
  }

  private static String signature(String payload, String secret) throws Exception {
    long timestamp = Instant.now().getEpochSecond();
    return "t="
        + timestamp
        + ",v1="
        + Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
  }

  private HttpResponse<String> webhook(String payload, String signature) throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/public/webhooks/stripe"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload));
    if (signature != null) request.header("Stripe-Signature", signature);
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
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
