package com.smartcommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.mail.VerificationMail;
import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SC-049 (ADR-0019 C1) over real HTTP and PostgreSQL: the OWNER invites an employee by e-mail, only
 * the invited verified account accepts, once and before expiry; revoking takes effect on the next
 * request; employees count against the owner's plan once per person; and an employee gets no
 * success from any store route of the OWNER, checked over every mapped route.
 */
@Testcontainers
@Import(StaffIT.MailConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "billing.enabled=true",
      "billing.stripe.secret-key=sk_test_synthetic",
      "billing.stripe.webhook-secret=whsec_synthetic_for_tests",
      "orders.maintenance.enabled=false",
      "advisor.housekeeping.enabled=false"
    })
class StaffIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic staff secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired CapturedMail mail;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  UUID owner;
  Browser boss;
  String store;

  @BeforeEach
  void ownerWithStore() throws Exception {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, billing_event, identity_rate_limit CASCADE");
    mail.links.clear();
    owner = activeIdentity("dona@example.test");
    boss = login("dona@example.test");
    store = create(boss, "loja-equipe");
  }

  @Test
  void anInvitedAccountJoinsOnceAndLosesAccessWhenRevoked() throws Exception {
    UUID employee = activeIdentity("caixa@example.test");
    var invited =
        boss.send("POST", staffPath("/invitations"), Map.of("email", "caixa@Example.test"));
    assertThat(invited.statusCode()).isEqualTo(201);
    String token = token("caixa@Example.test");

    Browser worker = login("caixa@example.test");
    var accepted = worker.send("POST", "/api/staff-invitations/acceptance", Map.of("token", token));
    assertThat(accepted.statusCode()).isEqualTo(200);
    assertThat(json(accepted).get("tenantId").asText()).isEqualTo(store);
    // The employee sees the company, marked STAFF; the token cannot be used again.
    JsonNode listed = json(worker.get("/api/tenants"));
    assertThat(listed.size()).isEqualTo(1);
    assertThat(listed.get(0).get("role").asText()).isEqualTo("STAFF");
    assertThat(json(worker.get("/api/tenants/" + store)).get("role").asText()).isEqualTo("STAFF");
    assertThat(
            worker
                .send("POST", "/api/staff-invitations/acceptance", Map.of("token", token))
                .statusCode())
        .isEqualTo(410);

    JsonNode overview = json(boss.get(staffPath("")));
    assertThat(overview.get("members").get(0).get("email").asText())
        .isEqualTo("caixa@example.test");
    assertThat(overview.get("invitations").size()).isZero();
    assertThat(overview.get("used").asLong()).isEqualTo(1);
    assertThat(overview.get("limit").asInt()).isEqualTo(3);

    // The employee manages nothing of the team.
    assertThat(worker.get(staffPath("")).statusCode()).isEqualTo(404);
    assertThat(
            worker
                .send("POST", staffPath("/invitations"), Map.of("email", "outro@example.test"))
                .statusCode())
        .isEqualTo(404);

    assertThat(boss.send("DELETE", staffPath("/" + employee), null).statusCode()).isEqualTo(204);
    assertThat(worker.get("/api/tenants/" + store).statusCode()).isEqualTo(404);
    assertThat(json(worker.get("/api/tenants")).size()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT access_revoked_at IS NOT NULL AND revoked_by=? FROM staff_invitation",
                Boolean.class,
                owner))
        .isTrue();
    assertThat(boss.send("DELETE", staffPath("/" + employee), null).statusCode()).isEqualTo(404);
  }

  @Test
  void onlyTheInvitedAccountAcceptsBeforeExpiryAndCanceledInvitationsDie() throws Exception {
    activeIdentity("convidado@example.test");
    activeIdentity("intruso@example.test");
    boss.send("POST", staffPath("/invitations"), Map.of("email", "convidado@example.test"));
    String token = token("convidado@example.test");

    var wrong =
        login("intruso@example.test")
            .send("POST", "/api/staff-invitations/acceptance", Map.of("token", token));
    assertThat(wrong.statusCode()).isEqualTo(403);
    assertThat(json(wrong).get("code").asText()).isEqualTo("WRONG_ACCOUNT");

    Browser invited = login("convidado@example.test");
    jdbc.update(
        "UPDATE staff_invitation SET created_at=now()-interval '9 days', expires_at=now()-interval '1 second'");
    assertThat(
            invited
                .send("POST", "/api/staff-invitations/acceptance", Map.of("token", token))
                .statusCode())
        .isEqualTo(410);
    assertThat(json(boss.get(staffPath(""))).get("invitations").get(0).get("expired").asBoolean())
        .isTrue();

    // A new invitation replaces the old one; canceling it kills its token too.
    boss.send("POST", staffPath("/invitations"), Map.of("email", "convidado@example.test"));
    String second = token("convidado@example.test");
    String invitation = json(boss.get(staffPath(""))).get("invitations").get(0).get("id").asText();
    assertThat(boss.send("DELETE", staffPath("/invitations/" + invitation), null).statusCode())
        .isEqualTo(204);
    assertThat(
            invited
                .send("POST", "/api/staff-invitations/acceptance", Map.of("token", second))
                .statusCode())
        .isEqualTo(410);
    assertThat(
            invited
                .send("POST", "/api/staff-invitations/acceptance", Map.of("token", "curto"))
                .statusCode())
        .isEqualTo(410);

    // The owner cannot invite himself, an invalid e-mail is refused, and nobody else's team is
    // reachable.
    var self = boss.send("POST", staffPath("/invitations"), Map.of("email", "dona@example.test"));
    assertThat(json(self).get("code").asText()).isEqualTo("ALREADY_MEMBER");
    assertThat(
            boss.send("POST", staffPath("/invitations"), Map.of("email", "sem-arroba"))
                .statusCode())
        .isEqualTo(400);
    activeIdentity("outra-dona@example.test");
    Browser other = login("outra-dona@example.test");
    create(other, "outra-loja");
    assertThat(other.get(staffPath("")).statusCode()).isEqualTo(404);
    assertThat(
            other
                .send("POST", staffPath("/invitations"), Map.of("email", "x@example.test"))
                .statusCode())
        .isEqualTo(404);
    assertThat(mail.links).doesNotContainKey("x@example.test");
  }

  @Test
  void employeesCountOncePerPersonAgainstTheOwnersPlan() throws Exception {
    // The trial unlocks the Profissional plan: 3 people, invitations included.
    for (String email : List.of("a@example.test", "b@example.test", "c@example.test"))
      assertThat(boss.send("POST", staffPath("/invitations"), Map.of("email", email)).statusCode())
          .isEqualTo(201);
    var fourth = boss.send("POST", staffPath("/invitations"), Map.of("email", "d@example.test"));
    assertThat(fourth.statusCode()).isEqualTo(409);
    assertThat(json(fourth).get("code").asText()).isEqualTo("PLAN_LIMIT");
    // Inviting the same person again replaces the invitation instead of taking a new slot, and so
    // does inviting her to the owner's second store.
    assertThat(
            boss.send("POST", staffPath("/invitations"), Map.of("email", "a@example.test"))
                .statusCode())
        .isEqualTo(201);
    String second = create(boss, "segunda-loja");
    assertThat(
            boss.send(
                    "POST",
                    "/api/tenants/" + second + "/staff/invitations",
                    Map.of("email", "b@example.test"))
                .statusCode())
        .isEqualTo(201);
    assertThat(json(boss.get(staffPath(""))).get("used").asLong()).isEqualTo(3);

    jdbc.update(
        "UPDATE billing_account SET status='ACTIVE', plan='ESSENCIAL', billing_cycle='MONTHLY',"
            + " current_period_end=now()+interval '20 days' WHERE user_id=?",
        owner);
    assertThat(json(boss.get(staffPath(""))).get("limit").asInt()).isEqualTo(1);
  }

  @Test
  void aShrunkPlanStopsPendingInvitationsAndInvitationMailsAreBounded() throws Exception {
    // Invited during the trial (Profissional, 3 people)...
    for (String email : List.of("um@example.test", "dois@example.test"))
      boss.send("POST", staffPath("/invitations"), Map.of("email", email));
    activeIdentity("um@example.test");
    activeIdentity("dois@example.test");
    String first = token("um@example.test"), second = token("dois@example.test");
    // ...and the owner moves to Essencial (1 person) before they accept: only one gets in.
    jdbc.update(
        "UPDATE billing_account SET status='ACTIVE', plan='ESSENCIAL', billing_cycle='MONTHLY',"
            + " current_period_end=now()+interval '20 days' WHERE user_id=?",
        owner);
    assertThat(
            login("um@example.test")
                .send("POST", "/api/staff-invitations/acceptance", Map.of("token", first))
                .statusCode())
        .isEqualTo(200);
    var refused =
        login("dois@example.test")
            .send("POST", "/api/staff-invitations/acceptance", Map.of("token", second));
    assertThat(refused.statusCode()).isEqualTo(409);
    assertThat(json(refused).get("code").asText()).isEqualTo("OWNER_LIMIT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tenant_membership WHERE role='STAFF'", Long.class))
        .isEqualTo(1);

    // The same recipient cannot be mailed again and again.
    jdbc.update("UPDATE billing_account SET plan='ESCALA' WHERE user_id=?", owner);
    int sent = 0;
    for (int attempt = 0; attempt < 6; attempt++)
      if (boss.send("POST", staffPath("/invitations"), Map.of("email", "alvo@example.test"))
              .statusCode()
          == 201) sent++;
    assertThat(sent).isEqualTo(5);
    var throttled =
        boss.send("POST", staffPath("/invitations"), Map.of("email", "alvo@example.test"));
    assertThat(throttled.statusCode()).isEqualTo(429);
    assertThat(json(throttled).get("code").asText()).isEqualTo("THROTTLED");
  }

  @Test
  void anEmployeeGetsNoSuccessFromAnyStoreRouteOfTheOwner() throws Exception {
    activeIdentity("operador@example.test");
    boss.send("POST", staffPath("/invitations"), Map.of("email", "operador@example.test"));
    Browser worker = login("operador@example.test");
    assertThat(
            worker
                .send(
                    "POST",
                    "/api/staff-invitations/acceptance",
                    Map.of("token", token("operador@example.test")))
                .statusCode())
        .isEqualTo(200);

    List<String> checked = new ArrayList<>();
    List<String> leaks = new ArrayList<>();
    for (var info : mappings.getHandlerMethods().keySet()) {
      if (info.getPathPatternsCondition() == null) continue;
      for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
        if (!pattern.startsWith("/api/tenants/{tenantId}")) continue;
        var methods = info.getMethodsCondition().getMethods();
        for (var method :
            methods.isEmpty()
                ? List.of(org.springframework.web.bind.annotation.RequestMethod.GET)
                : methods) {
          // The company itself is shown to its employees on purpose (to reach their features).
          if (pattern.equals("/api/tenants/{tenantId}") && method.name().equals("GET")) continue;
          // SC-052: the cash register is the employees' own screen (checked in CashIT).
          if (pattern.equals("/api/tenants/{tenantId}/cash") && method.name().equals("GET"))
            continue;
          // SC-053: so is the counter (checked in CounterSaleIT).
          if (pattern.equals("/api/tenants/{tenantId}/counter-sales")
              && method.name().equals("GET")) continue;
          String path =
              pattern
                  .replace("{tenantId}", store)
                  .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
          var response = worker.raw(method.name(), path, "{}", worker.csrfToken);
          checked.add(method + " " + pattern + " -> " + response.statusCode());
          // Success is a leak; 503 only means an integration switched off in this test (checked
          // before the OWNER, for everyone); any other 5xx would hide a failure.
          int status = response.statusCode();
          if (status < 400 || (status >= 500 && status != 503))
            leaks.add(method + " " + pattern + " -> " + response.statusCode());
        }
      }
    }
    assertThat(checked).hasSizeGreaterThan(40);
    assertThat(leaks).as("store routes answering an employee").isEmpty();
  }

  private String staffPath(String rest) {
    return "/api/tenants/" + store + "/staff" + rest;
  }

  private String token(String email) {
    String link = mail.links.get(email);
    assertThat(link).as("invitation link for " + email).isNotNull();
    assertThat(link).contains("/convite#token=");
    return link.substring(link.indexOf("#token=") + 7);
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

  /** Keeps the last link sent to each recipient; nothing leaves the test. */
  static final class CapturedMail implements VerificationMail {
    final Map<String, String> links = new ConcurrentHashMap<>();

    @Override
    public void send(String recipient, String link) {
      links.put(recipient, link);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MailConfiguration {
    @Bean
    @Primary
    CapturedMail capturedMail() {
      return new CapturedMail();
    }
  }
}
