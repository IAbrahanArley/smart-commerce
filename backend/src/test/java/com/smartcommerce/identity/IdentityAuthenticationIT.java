package com.smartcommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.exceptions.IdentityException;
import com.smartcommerce.identity.mail.VerificationMail;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP/cookies and PostgreSQL: no mocked security filters or password verification. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IdentityAuthenticationIT.MailConfiguration.class)
class IdentityAuthenticationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic integration secret 2026!";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired CapturedMail mail;
  @Autowired JdbcIndexedSessionRepository sessions;

  @BeforeEach
  void resetDisposableFixtures() {
    jdbc.update("DELETE FROM SPRING_SESSION");
    jdbc.update("DELETE FROM identity_email_verification");
    jdbc.update("DELETE FROM identity_rate_limit");
    jdbc.update("DELETE FROM identity_local_credential");
    jdbc.update("DELETE FROM identity_user");
    mail.links.clear();
    mail.fail = false;
  }

  @Test
  void registrationProofPasswordLoginAndLogoutPreserveBoundary() throws Exception {
    Browser browser = new Browser();
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    String token = register(browser, "Owner@EXAMPLE.TEST");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_local_credential", Integer.class))
        .isZero();
    assertThat(
            browser
                .post(
                    "/api/auth/login", Map.of("email", "Owner@example.test", "password", PASSWORD))
                .statusCode())
        .isEqualTo(401);
    // Following a mail link is inert; proof is consumed only with an explicit password POST.
    browser.get("/confirmar-email");
    assertThat(jdbc.queryForObject("SELECT status FROM identity_user", String.class))
        .isEqualTo("PENDING_EMAIL");
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", token, "password", "too short"))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            browser
                .post(
                    "/api/auth/login", Map.of("email", "Owner@example.test", "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    var response = browser.get("/api/auth/me");
    assertThat(response.statusCode()).isEqualTo(200);
    var identity = JSON.readTree(response.body());
    assertThat(identity.get("email").asText()).isEqualTo("Owner@EXAMPLE.TEST");
    assertThat(identity.has("id")).isTrue();
    assertThat(response.body())
        .doesNotContain("password", "hash", "authVersion", "roles", "tenant");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM SPRING_SESSION", Integer.class))
        .isPositive();
    assertThat(browser.post("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
  }

  @Test
  void csrfAndOriginProtectEveryPublicMutationIncludingLogin() throws Exception {
    Browser browser = new Browser();
    for (String route : new String[] {"registration", "activation", "login", "logout"}) {
      assertThat(browser.send("POST", "/api/auth/" + route, "{}", null, null).statusCode())
          .isEqualTo(403);
    }
    browser.csrf();
    assertThat(
            browser
                .send(
                    "POST",
                    "/api/auth/registration",
                    "{\"email\":\"csrf@example.test\"}",
                    "incorrect",
                    null)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            browser
                .send(
                    "POST",
                    "/api/auth/registration",
                    "{\"email\":\"csrf@example.test\"}",
                    browser.csrfToken,
                    "https://attacker.example")
                .statusCode())
        .isEqualTo(403);
    String oversized = "{\"email\":\"" + "a".repeat(20000) + "@example.test\"}";
    assertThat(
            browser
                .send("POST", "/api/auth/registration", oversized, browser.csrfToken, null)
                .statusCode())
        .isEqualTo(413);
    assertThat(mail.links).isEmpty();
  }

  @Test
  void expiredAndReplacedProofsCannotEstablishCredentials() throws Exception {
    Browser browser = new Browser();
    String oldToken = register(browser, "proof@example.test");
    jdbc.update(
        "UPDATE identity_email_verification SET issued_at = now() - interval '31 minutes', expires_at = now() - interval '1 minute'");
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", oldToken, "password", PASSWORD))
                .statusCode())
        .isEqualTo(400);
    String newToken = register(browser, "proof@example.test");
    assertThat(newToken).isNotEqualTo(oldToken);
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", oldToken, "password", PASSWORD))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", newToken, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
  }

  @Test
  void suppressedResendPreservesProofAndOnlyItsDigestIsPersisted() throws Exception {
    Browser browser = new Browser();
    String token = register(browser, "resend@example.test");
    String link = mail.links.get("resend@example.test");
    assertThat(
            browser
                .post("/api/auth/registration", Map.of("email", "resend@example.test"))
                .statusCode())
        .isEqualTo(202);
    assertThat(mail.links.get("resend@example.test")).isEqualTo(link);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM identity_email_verification", Integer.class))
        .isEqualTo(1);
    String digest =
        jdbc.queryForObject("SELECT token_digest FROM identity_email_verification", String.class);
    assertThat(digest).matches("[a-f0-9]{64}").isNotEqualTo(token);
    assertThat(
            jdbc.queryForObject(
                "SELECT extract(epoch FROM (expires_at-issued_at))::int FROM identity_email_verification",
                Integer.class))
        .isEqualTo(1800);
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
  }

  @Test
  void simultaneousProofConsumptionHasExactlyOneWinner() throws Exception {
    Browser first = new Browser();
    String token = register(first, "race@example.test");
    Browser second = new Browser();
    second.csrf();
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return first
                    .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                    .statusCode();
              });
      var two =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return second
                    .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                    .statusCode();
              });
      assertThat(java.util.List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(204, 400);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_local_credential", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void jdbcSessionRotatesOnLoginAndChecksAbsoluteIdleAndRevocation() throws Exception {
    Browser browser = activeBrowser("session@example.test");
    // An anonymous JDBC session is deliberately established to test fixation, not merely cookie
    // issuance.
    var anonymous = sessionRepository().createSession();
    sessionRepository().save(anonymous);
    browser.cookies.getCookieStore().removeAll();
    browser.addSessionCookie(anonymous.getId());
    browser.csrf();
    assertThat(
            browser
                .post(
                    "/api/auth/login",
                    Map.of("email", "session@example.test", "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    assertThat(sessionRepository().findById(anonymous.getId())).isNull();
    String current =
        jdbc.queryForObject(
            "SELECT SESSION_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NOT NULL", String.class);
    assertThat(current).isNotEqualTo(anonymous.getId());
    assertThat(sessionRepository().findById(current).getMaxInactiveInterval())
        .isEqualTo(Duration.ofMinutes(30));
    var session = sessionRepository().findById(current);
    session.setAttribute(
        "identity.authenticatedAt",
        Instant.now().minus(Duration.ofHours(8)).minusSeconds(1).toEpochMilli());
    sessionRepository().save(session);
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);

    login(browser, "session@example.test");
    current =
        jdbc.queryForObject(
            "SELECT SESSION_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NOT NULL", String.class);
    session = sessionRepository().findById(current);
    session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(31)));
    sessionRepository().save(session);
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);

    login(browser, "session@example.test");
    jdbc.update(
        "UPDATE identity_user SET auth_version = auth_version + 1 WHERE email_key = 'session@example.test'");
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
    login(browser, "session@example.test");
    jdbc.update(
        "UPDATE identity_user SET status = 'DISABLED' WHERE email_key = 'session@example.test'");
    assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
  }

  @Test
  void genericRegistrationDoesNotReplaceActivePasswordAndLoginFailuresAreBounded()
      throws Exception {
    Browser browser = activeBrowser("limits@example.test");
    String storedHash =
        jdbc.queryForObject("SELECT password_hash FROM identity_local_credential", String.class);
    mail.links.clear();
    var active = browser.post("/api/auth/registration", Map.of("email", "limits@example.test"));
    var absent = browser.post("/api/auth/registration", Map.of("email", "absent@example.test"));
    assertThat(active.statusCode()).isEqualTo(202);
    assertThat(active.body()).isEqualTo(absent.body());
    assertThat(mail.links).doesNotContainKey("limits@example.test");
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM identity_local_credential", String.class))
        .isEqualTo(storedHash);
    String firstBody = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      var failure =
          browser.post(
              "/api/auth/login",
              Map.of("email", "limits@example.test", "password", "Incorrect synthetic password"));
      assertThat(failure.statusCode()).isEqualTo(401);
      if (firstBody == null) firstBody = failure.body();
      else assertThat(failure.body()).isEqualTo(firstBody);
    }
    var limited =
        browser.post(
            "/api/auth/login", Map.of("email", "limits@example.test", "password", PASSWORD));
    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(limited.headers().firstValue("Retry-After")).isPresent();
  }

  @Test
  void deliveryFailureRemainsGenericAndPreservesThePreviousProof() throws Exception {
    Browser active = activeBrowser("active-mail@example.test");
    Browser pending = new Browser();
    String previous = register(pending, "pending-mail@example.test");
    jdbc.update("UPDATE identity_email_verification SET issued_at = now() - interval '61 seconds'");
    mail.fail = true;
    var activeResponse =
        active.post("/api/auth/registration", Map.of("email", "active-mail@example.test"));
    var pendingResponse =
        pending.post("/api/auth/registration", Map.of("email", "pending-mail@example.test"));
    var unknownResponse =
        pending.post("/api/auth/registration", Map.of("email", "unknown-mail@example.test"));
    assertThat(activeResponse.statusCode()).isEqualTo(202);
    assertThat(pendingResponse.statusCode()).isEqualTo(202);
    assertThat(unknownResponse.statusCode()).isEqualTo(202);
    assertThat(pendingResponse.body())
        .isEqualTo(activeResponse.body())
        .isEqualTo(unknownResponse.body());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM identity_user WHERE email_key='unknown-mail@example.test'",
                Integer.class))
        .isZero();
    assertThat(
            pending
                .post("/api/auth/activation", Map.of("token", previous, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
  }

  @SuppressWarnings("unchecked")
  private SessionRepository<Session> sessionRepository() {
    // Spring Session exposes its concrete session with package visibility; use its public contract.
    return (SessionRepository<Session>) (SessionRepository<?>) sessions;
  }

  private Browser activeBrowser(String email) throws Exception {
    Browser browser = new Browser();
    String token = register(browser, email);
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    return browser;
  }

  private void login(Browser browser, String email) throws Exception {
    browser.csrf();
    assertThat(
            browser
                .post("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
  }

  private String register(Browser browser, String email) throws Exception {
    browser.csrf();
    assertThat(browser.post("/api/auth/registration", Map.of("email", email)).statusCode())
        .isEqualTo(202);
    String link = mail.links.get(email);
    assertThat(link).isNotNull();
    URI action = URI.create(link);
    assertThat(action.getRawQuery()).isNull();
    assertThat(action.getRawFragment()).startsWith("token=");
    return action.getRawFragment().replaceFirst("^token=", "");
  }

  private final class Browser {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    void addSessionCookie(String id) {
      var cookie =
          new java.net.HttpCookie(
              "SCSESSION",
              java.util.Base64.getEncoder()
                  .encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      cookie.setPath("/");
      cookie.setVersion(0);
      cookies.getCookieStore().add(URI.create("http://127.0.0.1:" + port), cookie);
    }

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      var json = JSON.readTree(response.body());
      assertThat(json.get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
      csrfToken = json.get("token").asText();
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null, null);
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
      return send("POST", path, JSON.writeValueAsString(body), csrfToken, null);
    }

    HttpResponse<String> send(String method, String path, String body, String csrf, String origin)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(20));
      if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
      if (origin != null) request.header("Origin", origin);
      if (body != null) request.header("Content-Type", "application/json");
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(body));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  static final class CapturedMail implements VerificationMail {
    final Map<String, String> links = new ConcurrentHashMap<>();
    volatile boolean fail;

    @Override
    public void send(String recipient, String activationLink) {
      if (fail)
        throw new IdentityException(
            IdentityException.Reason.UNAVAILABLE, "Synthetic mail transport unavailable");
      links.put(recipient, activationLink);
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
