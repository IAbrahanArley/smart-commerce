package com.smartcommerce.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
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
import java.util.regex.Pattern;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-0017 B13 over real HTTP and PostgreSQL against a fake Stripe API: an upgrade previews the
 * prorated charge and applies it only with a fresh preview, a declined card leaves the plan as it
 * was, a downgrade is scheduled for the end of the period and can be undone, cancellation waits for
 * the end of the period and can be reverted, and an owner only ever reaches his own subscription.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "billing.enabled=true",
      "billing.stripe.secret-key=sk_test_synthetic",
      "billing.stripe.webhook-secret=whsec_synthetic_for_tests",
      "orders.maintenance.enabled=false",
      "advisor.housekeeping.enabled=false"
    })
class StripePlanChangeIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic plan change secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final long PERIOD_START = 1_790_000_000L;
  private static final long PERIOD_END = 1_830_000_000L;
  private static final Pattern PRICE = Pattern.compile("price_([a-z_]+)");
  private static final Pattern NEXT_PRICE =
      Pattern.compile("phases\\[1]\\[items]\\[0]\\[price]=price_([a-z_]+)");
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  private static final Fake FAKE = new Fake();
  private static final HttpServer STRIPE = stripe();

  /** What the fake Stripe holds for the one subscription of these tests. */
  private static final class Fake {
    volatile String status;
    volatile String lookupKey;
    volatile boolean cancelAtEnd;
    volatile String pending;
    volatile boolean declining;
  }

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
            String reply = reply(exchange.getRequestMethod(), path, query, body);
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

  private static String reply(String method, String path, String query, String body) {
    return switch (path) {
      case "/v1/prices" -> {
        String key = query.replaceAll(".*lookup_keys\\[0]=([a-z_]+).*", "$1");
        yield "{\"object\":\"list\",\"url\":\"/v1/prices\",\"has_more\":false,\"data\":[{\"id\":\"price_"
            + key
            + "\",\"object\":\"price\",\"lookup_key\":\""
            + key
            + "\"}]}";
      }
      case "/v1/subscriptions" ->
          "{\"object\":\"list\",\"url\":\"/v1/subscriptions\",\"has_more\":false,\"data\":["
              + (FAKE.lookupKey == null ? "" : subscription(null))
              + "]}";
      case "/v1/subscriptions/sub_synthetic" -> {
        if (body.contains("cancel_at_period_end=true")) FAKE.cancelAtEnd = true;
        if (body.contains("cancel_at_period_end=false")) FAKE.cancelAtEnd = false;
        String pendingUpdate = null;
        var price = PRICE.matcher(body);
        if (body.contains("items[0][price]") && price.find()) {
          if (FAKE.declining) pendingUpdate = "{\"expires_at\":" + (PERIOD_START + 82_800) + "}";
          else FAKE.lookupKey = price.group(1);
        }
        yield subscription(pendingUpdate);
      }
      // The prorated amount depends on the target price, so another plan gives another amount.
      case "/v1/invoices/create_preview" ->
          "{\"id\":\"upcoming_in_1\",\"object\":\"invoice\",\"amount_due\":"
              + (body.contains("price_escala") ? 99_900 : 7_012)
              + ",\"currency\":\"brl\"}";
      case "/v1/invoices/in_open/void" ->
          "{\"id\":\"in_open\",\"object\":\"invoice\",\"status\":\"void\"}";
      case "/v1/subscription_schedules" -> schedule();
      case "/v1/subscription_schedules/sub_sched_1" -> {
        if ("POST".equals(method)) {
          var next = NEXT_PRICE.matcher(body);
          if (next.find()) FAKE.pending = next.group(1);
        }
        yield schedule();
      }
      case "/v1/subscription_schedules/sub_sched_1/release" -> {
        FAKE.pending = null;
        yield "{\"id\":\"sub_sched_1\",\"object\":\"subscription_schedule\",\"status\":\"released\",\"phases\":[]}";
      }
      default -> null;
    };
  }

  private static String subscription(String pendingUpdate) {
    return "{\"id\":\"sub_synthetic\",\"object\":\"subscription\",\"status\":\""
        + FAKE.status
        + "\",\"created\":1790000000,\"latest_invoice\":"
        + (pendingUpdate == null ? "null" : "\"in_open\"")
        + ",\"cancel_at_period_end\":"
        + FAKE.cancelAtEnd
        + ",\"customer\":\"cus_synthetic\",\"schedule\":"
        + (FAKE.pending == null ? "null" : "\"sub_sched_1\"")
        + ",\"pending_update\":"
        + (pendingUpdate == null ? "null" : pendingUpdate)
        + ",\"items\":{\"object\":\"list\",\"data\":[{\"id\":\"si_1\",\"object\":\"subscription_item\","
        + "\"current_period_start\":"
        + PERIOD_START
        + ",\"current_period_end\":"
        + PERIOD_END
        + ",\"price\":{\"id\":\"price_"
        + FAKE.lookupKey
        + "\",\"object\":\"price\",\"lookup_key\":\""
        + FAKE.lookupKey
        + "\"}}]}}";
  }

  private static String schedule() {
    String current =
        "{\"start_date\":"
            + PERIOD_START
            + ",\"end_date\":"
            + PERIOD_END
            + ",\"items\":[{\"price\":\"price_"
            + FAKE.lookupKey
            + "\",\"quantity\":1}]}";
    String next =
        FAKE.pending == null
            ? ""
            : ",{\"start_date\":"
                + PERIOD_END
                + ",\"end_date\":"
                + (PERIOD_END + 2_592_000)
                + ",\"items\":[{\"price\":{\"id\":\"price_"
                + FAKE.pending
                + "\",\"object\":\"price\",\"lookup_key\":\""
                + FAKE.pending
                + "\"},\"quantity\":1}]}";
    return "{\"id\":\"sub_sched_1\",\"object\":\"subscription_schedule\",\"status\":\"active\","
        + "\"current_phase\":{\"start_date\":"
        + PERIOD_START
        + ",\"end_date\":"
        + PERIOD_END
        + "},\"phases\":["
        + current
        + next
        + "]}";
  }

  @BeforeEach
  void subscribedOwner() throws Exception {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, billing_event CASCADE");
    REQUESTS.clear();
    FAKE.status = "active";
    FAKE.lookupKey = "essencial_mensal";
    FAKE.cancelAtEnd = false;
    FAKE.pending = null;
    FAKE.declining = false;
    owner = activeIdentity("assinante@example.test");
    login("assinante@example.test").get("/api/billing/account");
    jdbc.update(
        "UPDATE billing_account SET stripe_customer_id='cus_synthetic', status='ACTIVE',"
            + " plan='ESSENCIAL', billing_cycle='MONTHLY' WHERE user_id=?",
        owner);
  }

  @Test
  void anUpgradeChargesThePreviewedAmountAndAppliesAtOnce() throws Exception {
    Browser a = login("assinante@example.test");
    var preview =
        json(
            a.send(
                "POST",
                "/api/billing/change/preview",
                Map.of("plan", "PROFISSIONAL", "cycle", "MONTHLY")));
    assertThat(preview.get("timing").asText()).isEqualTo("NOW");
    assertThat(preview.get("amountDue").decimalValue()).isEqualByComparingTo("70.12");
    assertThat(preview.get("currency").asText()).isEqualTo("BRL");
    long prorationDate = preview.get("prorationDate").asLong();
    assertThat(REQUESTS)
        .anyMatch(
            r ->
                r.startsWith("POST /v1/invoices/create_preview")
                    && r.contains("price_profissional_mensal")
                    && r.contains("proration_behavior]=always_invoice")
                    && r.contains("proration_date]=" + prorationDate));

    // Without the preview's date and amount, with an old or future date, with another amount or
    // with another plan on the previewed date, nothing is charged.
    long now = Instant.now().getEpochSecond();
    for (var refused :
        List.of(
            Map.of("plan", "PROFISSIONAL", "cycle", "MONTHLY"),
            Map.of("plan", "PROFISSIONAL", "cycle", "MONTHLY", "prorationDate", prorationDate),
            Map.of(
                "plan",
                "PROFISSIONAL",
                "cycle",
                "MONTHLY",
                "prorationDate",
                now - 3_600,
                "amountDue",
                "70.12"),
            Map.of(
                "plan",
                "PROFISSIONAL",
                "cycle",
                "MONTHLY",
                "prorationDate",
                now + 600,
                "amountDue",
                "70.12"),
            Map.of(
                "plan",
                "PROFISSIONAL",
                "cycle",
                "MONTHLY",
                "prorationDate",
                prorationDate,
                "amountDue",
                "1.00"),
            Map.of(
                "plan",
                "ESCALA",
                "cycle",
                "MONTHLY",
                "prorationDate",
                prorationDate,
                "amountDue",
                "70.12"))) {
      var response = a.send("POST", "/api/billing/change", refused);
      assertThat(response.statusCode()).as(refused.toString()).isEqualTo(409);
      assertThat(json(response).get("code").asText()).isEqualTo("PREVIEW_EXPIRED");
    }
    assertThat(REQUESTS).noneMatch(r -> r.startsWith("POST /v1/subscriptions/sub_synthetic"));

    var changed =
        a.send(
            "POST",
            "/api/billing/change",
            Map.of(
                "plan",
                "PROFISSIONAL",
                "cycle",
                "MONTHLY",
                "prorationDate",
                prorationDate,
                "amountDue",
                "70.12"));
    assertThat(changed.statusCode()).isEqualTo(200);
    assertThat(json(changed).get("plan").asText()).isEqualTo("PROFISSIONAL");
    assertThat(REQUESTS)
        .anyMatch(
            r ->
                r.startsWith("POST /v1/subscriptions/sub_synthetic")
                    && r.contains("items[0][id]=si_1")
                    && r.contains("items[0][price]=price_profissional_mensal")
                    && r.contains("proration_behavior=always_invoice")
                    && r.contains("payment_behavior=pending_if_incomplete")
                    && r.contains("proration_date=" + prorationDate));
    // The account follows the subscription the API now reports.
    assertThat(plan()).isEqualTo("PROFISSIONAL");
    // A second click finds the plan already changed and charges nothing more.
    var again =
        a.send(
            "POST",
            "/api/billing/change",
            Map.of(
                "plan",
                "PROFISSIONAL",
                "cycle",
                "MONTHLY",
                "prorationDate",
                prorationDate,
                "amountDue",
                "70.12"));
    assertThat(json(again).get("code").asText()).isEqualTo("CHANGE_NOT_ALLOWED");
    assertThat(REQUESTS.stream().filter(r -> r.startsWith("POST /v1/subscriptions/sub_synthetic")))
        .hasSize(1);
  }

  @Test
  void aLongerCycleOnTheSamePlanIsAnUpgradeAndUnpaidSubscriptionsCannotChange() throws Exception {
    Browser a = login("assinante@example.test");
    var preview =
        json(
            a.send(
                "POST",
                "/api/billing/change/preview",
                Map.of("plan", "ESSENCIAL", "cycle", "ANNUAL")));
    assertThat(preview.get("timing").asText()).isEqualTo("NOW");
    var changed =
        a.send(
            "POST",
            "/api/billing/change",
            Map.of(
                "plan",
                "ESSENCIAL",
                "cycle",
                "ANNUAL",
                "prorationDate",
                preview.get("prorationDate").asLong(),
                "amountDue",
                preview.get("amountDue").decimalValue()));
    assertThat(json(changed).get("cycle").asText()).isEqualTo("ANNUAL");
    assertThat(
            jdbc.queryForObject(
                "SELECT billing_cycle FROM billing_account WHERE user_id=?", String.class, owner))
        .isEqualTo("ANNUAL");

    FAKE.status = "past_due";
    var unpaid =
        a.send("POST", "/api/billing/change/preview", Map.of("plan", "ESCALA", "cycle", "ANNUAL"));
    assertThat(unpaid.statusCode()).isEqualTo(409);
    assertThat(json(unpaid).get("code").asText()).isEqualTo("CHANGE_NOT_ALLOWED");
  }

  @Test
  void aDeclinedCardLeavesThePlanAsItWas() throws Exception {
    Browser a = login("assinante@example.test");
    FAKE.declining = true;
    long prorationDate =
        json(a.send(
                "POST", "/api/billing/change/preview", Map.of("plan", "ESCALA", "cycle", "ANNUAL")))
            .get("prorationDate")
            .asLong();
    var declined =
        a.send(
            "POST",
            "/api/billing/change",
            Map.of(
                "plan",
                "ESCALA",
                "cycle",
                "ANNUAL",
                "prorationDate",
                prorationDate,
                "amountDue",
                "999.00"));
    assertThat(declined.statusCode()).isEqualTo(402);
    assertThat(json(declined).get("code").asText()).isEqualTo("PAYMENT_DECLINED");
    // The open invoice is voided, so it can never be paid later and apply the upgrade.
    assertThat(REQUESTS).anyMatch(r -> r.startsWith("POST /v1/invoices/in_open/void"));
    assertThat(plan()).isEqualTo("ESSENCIAL");
    assertThat(json(a.get("/api/billing/subscription")).get("plan").asText())
        .isEqualTo("ESSENCIAL");
  }

  @Test
  void aDowngradeIsScheduledForThePeriodEndAndCanBeUndone() throws Exception {
    FAKE.lookupKey = "escala_anual";
    jdbc.update("UPDATE billing_account SET plan='ESCALA', billing_cycle='ANNUAL'");
    Browser a = login("assinante@example.test");

    var preview =
        json(
            a.send(
                "POST",
                "/api/billing/change/preview",
                Map.of("plan", "ESSENCIAL", "cycle", "MONTHLY")));
    assertThat(preview.get("timing").asText()).isEqualTo("AT_PERIOD_END");
    assertThat(preview.get("amountDue").isNull()).isTrue();
    assertThat(Instant.parse(preview.get("effectiveAt").asText()).getEpochSecond())
        .isEqualTo(PERIOD_END);
    assertThat(REQUESTS).noneMatch(r -> r.startsWith("POST /v1/invoices/create_preview"));

    // No proration date needed: nothing is charged now.
    var scheduled =
        json(
            a.send("POST", "/api/billing/change", Map.of("plan", "ESSENCIAL", "cycle", "MONTHLY")));
    assertThat(scheduled.get("plan").asText()).isEqualTo("ESCALA");
    assertThat(scheduled.get("pendingChange").get("plan").asText()).isEqualTo("ESSENCIAL");
    assertThat(scheduled.get("pendingChange").get("cycle").asText()).isEqualTo("MONTHLY");
    assertThat(REQUESTS)
        .anyMatch(
            r ->
                r.startsWith("POST /v1/subscription_schedules ")
                    && r.contains("from_subscription=sub_synthetic"))
        .anyMatch(
            r ->
                r.startsWith("POST /v1/subscription_schedules/sub_sched_1 ")
                    && r.contains("phases[0][end_date]=" + PERIOD_END)
                    && r.contains("phases[1][items][0][price]=price_essencial_mensal")
                    && r.contains("phases[1][duration][interval]=month")
                    && r.contains("phases[1][duration][interval_count]=1")
                    && r.contains("end_behavior=release"));
    // The account keeps the paid plan until the period ends.
    assertThat(plan()).isEqualTo("ESCALA");

    // One change at a time: another change or a cancellation waits until this one is undone, and
    // nothing releases the schedule behind the owner's back.
    var another =
        a.send("POST", "/api/billing/change/preview", Map.of("plan", "ESCALA", "cycle", "MONTHLY"));
    assertThat(json(another).get("code").asText()).isEqualTo("CHANGE_NOT_ALLOWED");
    assertThat(json(a.send("POST", "/api/billing/cancel", null)).get("code").asText())
        .isEqualTo("CHANGE_NOT_ALLOWED");
    assertThat(REQUESTS)
        .noneMatch(r -> r.contains("/release") || r.contains("cancel_at_period_end"));

    var undone = a.send("DELETE", "/api/billing/change", null);
    assertThat(undone.statusCode()).isEqualTo(200);
    assertThat(json(undone).get("pendingChange").isNull()).isTrue();
    assertThat(REQUESTS)
        .anyMatch(r -> r.startsWith("POST /v1/subscription_schedules/sub_sched_1/release"));
    assertThat(json(a.send("DELETE", "/api/billing/change", null)).get("code").asText())
        .isEqualTo("CHANGE_NOT_ALLOWED");
  }

  @Test
  void cancellationWaitsForThePeriodEndAndCanBeReverted() throws Exception {
    Browser a = login("assinante@example.test");
    var canceled = json(a.send("POST", "/api/billing/cancel", null));
    assertThat(canceled.get("cancelAtPeriodEnd").asBoolean()).isTrue();
    assertThat(REQUESTS)
        .anyMatch(
            r ->
                r.startsWith("POST /v1/subscriptions/sub_synthetic")
                    && r.contains("cancel_at_period_end=true"));
    // The plan stays paid until the end; changes wait until the owner keeps the subscription.
    assertThat(plan()).isEqualTo("ESSENCIAL");
    var refused =
        a.send(
            "POST",
            "/api/billing/change/preview",
            Map.of("plan", "PROFISSIONAL", "cycle", "MONTHLY"));
    assertThat(refused.statusCode()).isEqualTo(409);
    assertThat(json(refused).get("code").asText()).isEqualTo("CHANGE_NOT_ALLOWED");

    var resumed = json(a.send("POST", "/api/billing/resume", null));
    assertThat(resumed.get("cancelAtPeriodEnd").asBoolean()).isFalse();
    var same =
        a.send(
            "POST", "/api/billing/change/preview", Map.of("plan", "ESSENCIAL", "cycle", "MONTHLY"));
    assertThat(json(same).get("code").asText()).isEqualTo("CHANGE_NOT_ALLOWED");
  }

  @Test
  void anOwnerOnlyReachesHisOwnSubscription() throws Exception {
    activeIdentity("outro@example.test");
    Browser other = login("outro@example.test");
    assertThat(json(other.get("/api/billing/subscription")).get("plan").isNull()).isTrue();
    var cancel = other.send("POST", "/api/billing/cancel", null);
    assertThat(cancel.statusCode()).isEqualTo(409);
    assertThat(json(cancel).get("code").asText()).isEqualTo("NO_SUBSCRIPTION");
    assertThat(REQUESTS).noneMatch(r -> r.contains("cancel_at_period_end"));

    var anonymous = new Browser();
    anonymous.csrf();
    assertThat(anonymous.get("/api/billing/subscription").statusCode()).isEqualTo(401);
    assertThat(anonymous.send("POST", "/api/billing/cancel", null).statusCode()).isEqualTo(401);
  }

  private String plan() {
    return jdbc.queryForObject(
        "SELECT plan FROM billing_account WHERE user_id=?", String.class, owner);
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
