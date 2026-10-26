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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * MVP block 1a: password recovery over real HTTP, cookies and PostgreSQL. The request never tells
 * whether an account exists; the link is single use and short lived; the new password follows the
 * policy; and every existing session ends.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PasswordResetIT.MailConfiguration.class)
class PasswordResetIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic old secret 2026!";
  private static final String NEW_PASSWORD = "Synthetic new secret 2026!";
  private static final String GENERIC =
      "Se houver uma conta ativa com este e-mail, enviaremos um link para redefinir a senha.";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired CapturedMail mail;

  @BeforeEach
  void resetDisposableFixtures() {
    jdbc.update("DELETE FROM SPRING_SESSION");
    jdbc.update("DELETE FROM identity_password_reset");
    jdbc.update("DELETE FROM identity_email_verification");
    jdbc.update("DELETE FROM identity_rate_limit");
    jdbc.update("DELETE FROM identity_local_credential");
    jdbc.update("DELETE FROM identity_user");
    mail.activations.clear();
    mail.resets.clear();
    mail.fail = false;
  }

  @Test
  void resetReplacesThePasswordOnceAndEndsEverySession() throws Exception {
    activeAccount("Lojista@Example.test");
    Browser signedIn = login("Lojista@example.test", PASSWORD);
    assertThat(signedIn.get("/api/auth/me").statusCode()).isEqualTo(200);

    Browser anonymous = new Browser();
    anonymous.csrf();
    var requested =
        anonymous.post("/api/auth/password-reset/request", Map.of("email", "Lojista@EXAMPLE.test"));
    assertThat(requested.statusCode()).isEqualTo(202);
    assertThat(JSON.readTree(requested.body()).get("message").asText()).isEqualTo(GENERIC);
    assertThat(mail.resets).hasSize(1);
    URI link = URI.create(mail.resets.getFirst());
    assertThat(link.getPath()).isEqualTo("/redefinir-senha");
    assertThat(link.getRawQuery()).isNull(); // the token travels in the fragment, never to servers
    String token = link.getRawFragment().replaceFirst("^token=", "");
    assertThat(
            jdbc.queryForObject("SELECT token_digest FROM identity_password_reset", String.class))
        .isNotEqualTo(token)
        .hasSize(64);

    // A password outside the policy is refused and keeps the link usable.
    assertThat(confirm(anonymous, token, "curta").statusCode()).isEqualTo(400);
    assertThat(confirm(anonymous, token, NEW_PASSWORD).statusCode()).isEqualTo(204);
    // The old session ends, the old password stops working, the link cannot be reused.
    assertThat(signedIn.get("/api/auth/me").statusCode()).isEqualTo(401);
    assertThat(login(new Browser(), "Lojista@example.test", PASSWORD, 401)).isNull();
    assertThat(confirm(anonymous, token, "Another synthetic secret 2026!").statusCode())
        .isEqualTo(400);
    assertThat(login("Lojista@example.test", NEW_PASSWORD).get("/api/auth/me").statusCode())
        .isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_password_reset", Integer.class))
        .isZero();
  }

  @Test
  void requestsNeverRevealWhetherAnAccountExists() throws Exception {
    activeAccount("ativa@example.test");
    Browser browser = new Browser();
    browser.csrf();
    // Pending (never activated) account.
    assertThat(
            browser
                .post("/api/auth/registration", Map.of("email", "pendente@example.test"))
                .statusCode())
        .isEqualTo(202);
    for (String email :
        List.of("desconhecida@example.test", "pendente@example.test", "nao-e-email", "")) {
      var response = browser.post("/api/auth/password-reset/request", Map.of("email", email));
      assertThat(response.statusCode()).as(email).isEqualTo(202);
      assertThat(JSON.readTree(response.body()).get("message").asText()).isEqualTo(GENERIC);
    }
    assertThat(mail.resets).isEmpty();

    // Delivery failure: same answer, nothing stored, so the next request can try again.
    mail.fail = true;
    var failed =
        browser.post("/api/auth/password-reset/request", Map.of("email", "ativa@example.test"));
    assertThat(failed.statusCode()).isEqualTo(202);
    assertThat(JSON.readTree(failed.body()).get("message").asText()).isEqualTo(GENERIC);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_password_reset", Integer.class))
        .isZero();
    mail.fail = false;

    // One link per minute per account, three requests per hour per address.
    browser.post("/api/auth/password-reset/request", Map.of("email", "ativa@example.test"));
    browser.post("/api/auth/password-reset/request", Map.of("email", "ativa@example.test"));
    assertThat(mail.resets).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_password_reset", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void invalidExpiredOrUnprotectedConfirmationsAreRefused() throws Exception {
    activeAccount("expira@example.test");
    Browser browser = new Browser();
    // CSRF protects both routes like every other auth form.
    for (String route : List.of("request", "confirmation"))
      assertThat(browser.send("POST", "/api/auth/password-reset/" + route, "{}", null).statusCode())
          .isEqualTo(403);
    browser.csrf();
    assertThat(confirm(browser, "curto-demais", NEW_PASSWORD).statusCode()).isEqualTo(400);
    assertThat(confirm(browser, "A".repeat(43), NEW_PASSWORD).statusCode()).isEqualTo(400);

    browser.post("/api/auth/password-reset/request", Map.of("email", "expira@example.test"));
    String token = URI.create(mail.resets.getFirst()).getRawFragment().replaceFirst("^token=", "");
    jdbc.update(
        "UPDATE identity_password_reset SET issued_at=now()-interval '2 hours', expires_at=now()-interval '1 hour'");
    var expired = confirm(browser, token, NEW_PASSWORD);
    assertThat(expired.statusCode()).isEqualTo(400);
    assertThat(expired.body()).contains("Peça um novo link");
    assertThat(login(new Browser(), "expira@example.test", PASSWORD, 204)).isNotNull();
  }

  // --- helpers -----------------------------------------------------------------------------

  private void activeAccount(String email) throws Exception {
    Browser browser = new Browser();
    browser.csrf();
    assertThat(browser.post("/api/auth/registration", Map.of("email", email)).statusCode())
        .isEqualTo(202);
    String token =
        URI.create(mail.activations.get(email)).getRawFragment().replaceFirst("^token=", "");
    assertThat(
            browser
                .post("/api/auth/activation", Map.of("token", token, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
  }

  private Browser login(String email, String password) throws Exception {
    return login(new Browser(), email, password, 204);
  }

  private Browser login(Browser browser, String email, String password, int expected)
      throws Exception {
    browser.csrf();
    int status =
        browser.post("/api/auth/login", Map.of("email", email, "password", password)).statusCode();
    assertThat(status).isEqualTo(expected);
    if (status != 204) return null;
    browser.csrf();
    return browser;
  }

  private HttpResponse<String> confirm(Browser browser, String token, String password)
      throws Exception {
    return browser.post(
        "/api/auth/password-reset/confirmation", Map.of("token", token, "password", password));
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
      return send("GET", path, null, null);
    }

    HttpResponse<String> post(String path, Object body) throws Exception {
      return send("POST", path, JSON.writeValueAsString(body), csrfToken);
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

  /** Captures activation and reset links apart; can simulate a delivery failure. */
  static final class CapturedMail implements VerificationMail {
    final Map<String, String> activations = new ConcurrentHashMap<>();
    final List<String> resets = new CopyOnWriteArrayList<>();
    volatile boolean fail;

    @Override
    public void send(String recipient, String activationLink) {
      activations.put(recipient, activationLink);
    }

    @Override
    public void sendPasswordReset(String recipient, String resetLink) {
      if (fail)
        throw new IdentityException(
            IdentityException.Reason.UNAVAILABLE, "Synthetic mail transport unavailable");
      resets.add(resetLink);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MailConfiguration {
    @Bean
    @Primary
    CapturedMail capturedResetMail() {
      return new CapturedMail();
    }
  }
}
