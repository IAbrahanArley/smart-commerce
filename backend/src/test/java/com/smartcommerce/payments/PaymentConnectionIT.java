package com.smartcommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.Pkce;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.services.PaymentConnectionService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * SC-012 against a loopback fake of Mercado Pago's {@code /oauth/token}: PKCE, single-use state
 * bound to user and store, encrypted tokens, rotation on refresh, revocation, A/B isolation and no
 * secret in responses or logs.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"payments.token-refresh.enabled=false", "orders.maintenance.enabled=false"})
class PaymentConnectionIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic payments integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String CLIENT_SECRET = "synthetic-client-secret-for-tests";
  private static final String ORIGIN = "http://127.0.0.1:3000";
  private static final String REDIRECT = ORIGIN + "/api/payments/mercado-pago/callback";
  private static final List<JsonNode> TOKEN_REQUESTS = new CopyOnWriteArrayList<>();
  private static final AtomicReference<Reply> NEXT = new AtomicReference<>();
  private static final HttpServer PROVIDER = provider();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PaymentConnectionService connections;
  @Autowired PaymentProviders providers;
  UUID ownerA;
  UUID ownerB;
  UUID tenantA;
  UUID tenantB;

  record Reply(int status, String body) {}

  private static HttpServer provider() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/oauth/token",
          exchange -> {
            TOKEN_REQUESTS.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            Reply reply = NEXT.get();
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void mercadoPago(DynamicPropertyRegistry registry) {
    registry.add("payments.mercado-pago.client-id", () -> "1234567890");
    registry.add("payments.mercado-pago.client-secret", () -> CLIENT_SECRET);
    registry.add("payments.mercado-pago.redirect-uri", () -> REDIRECT);
    registry.add(
        "payments.mercado-pago.token-url",
        () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/oauth/token");
    registry.add(
        "payments.token-encryption-key",
        () -> Base64.getEncoder().encodeToString("synthetic-32-byte-test-key-00000".getBytes()));
    registry.add("identity.public-origin", () -> ORIGIN);
  }

  @AfterAll
  static void stopProvider() {
    PROVIDER.stop(0);
  }

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    TOKEN_REQUESTS.clear();
    NEXT.set(tokens("APP_USR-access-1", "TG-refresh-1", 15552000));
    ownerA = activeIdentity("payments-a@example.test");
    ownerB = activeIdentity("payments-b@example.test");
    tenantA = store(ownerA, "pagamentos-a");
    tenantB = store(ownerB, "pagamentos-b");
  }

  @Test
  void ownerConnectsWithPkceAndOnlySeesTheAccountIdNeverATokenOrSecret(CapturedOutput output)
      throws Exception {
    Browser a = login("payments-a@example.test");
    JsonNode before = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(before.get("status").asString()).isEqualTo("NOT_CONNECTED");

    Map<String, String> query = startAuthorization(a, tenantA);
    assertThat(query)
        .containsEntry("client_id", "1234567890")
        .containsEntry("response_type", "code")
        .containsEntry("platform_id", "mp")
        .containsEntry("redirect_uri", REDIRECT)
        .containsEntry("code_challenge_method", "S256");
    assertThat(Pkce.wellFormed(query.get("state"))).isTrue();

    var callback = a.get(callback("TG-code-1", query.get("state")));
    assertThat(callback.statusCode()).isEqualTo(303);
    assertThat(callback.headers().firstValue("Location").orElseThrow())
        .contains(ORIGIN + "/lojas/" + tenantA + "/pagamentos?mercadoPago=conectado");
    assertThat(callback.headers().firstValue("Cache-Control").orElse("")).contains("no-store");

    assertThat(TOKEN_REQUESTS).hasSize(1);
    JsonNode exchange = TOKEN_REQUESTS.getFirst();
    assertThat(exchange.get("grant_type").asString()).isEqualTo("authorization_code");
    assertThat(exchange.get("code").asString()).isEqualTo("TG-code-1");
    assertThat(exchange.get("client_secret").asString()).isEqualTo(CLIENT_SECRET);
    assertThat(exchange.get("redirect_uri").asString()).isEqualTo(REDIRECT);
    assertThat(exchange.get("test_token").asString()).isEqualTo("true");
    // PKCE: the verifier sent in the exchange is the one behind the challenge in the URL.
    assertThat(Pkce.challenge(exchange.get("code_verifier").asString()))
        .isEqualTo(query.get("code_challenge"));

    var status = a.get(path(tenantA));
    JsonNode connected = JSON.readTree(status.body());
    assertThat(connected.get("status").asString()).isEqualTo("CONNECTED");
    assertThat(connected.get("accountId").asString()).isEqualTo("987654");
    assertThat(status.body()).doesNotContain("APP_USR", "TG-", CLIENT_SECRET);

    Map<String, Object> stored =
        jdbc.queryForMap(
            "SELECT access_token, refresh_token FROM payment_account WHERE tenant_id=?", tenantA);
    for (Object value : stored.values())
      assertThat(new String((byte[]) value, StandardCharsets.ISO_8859_1))
          .doesNotContain("APP_USR", "TG-refresh");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payment_oauth_state WHERE used_at IS NOT NULL",
                Integer.class))
        .isEqualTo(1);

    assertThat(output.getAll())
        .doesNotContain(
            "APP_USR-access-1",
            "TG-refresh-1",
            "TG-code-1",
            CLIENT_SECRET,
            query.get("state"),
            exchange.get("code_verifier").asString());
  }

  @Test
  void stateIsSingleUseBoundToItsUserAndExpires() throws Exception {
    Browser a = login("payments-a@example.test");
    Browser b = login("payments-b@example.test");
    String state = startAuthorization(a, tenantA).get("state");

    // Another owner's session cannot use A's state, and the attempt does not consume it.
    assertThat(b.get(callback("TG-code", state)).headers().firstValue("Location").orElseThrow())
        .contains(ORIGIN + "/lojas?mercadoPago=expirado");
    assertThat(TOKEN_REQUESTS).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_account", Integer.class)).isZero();

    // Tampered or malformed states never reach the provider.
    for (String forged : List.of(Pkce.randomValue(), state.substring(1) + "A", "abc", ""))
      assertThat(a.get(callback("TG-code", forged)).headers().firstValue("Location").orElseThrow())
          .contains(ORIGIN + "/lojas?mercadoPago=expirado");
    assertThat(TOKEN_REQUESTS).isEmpty();

    assertThat(a.get(callback("TG-code", state)).headers().firstValue("Location").orElseThrow())
        .contains("/lojas/" + tenantA + "/pagamentos?mercadoPago=conectado");
    // Reuse after success.
    assertThat(a.get(callback("TG-code", state)).headers().firstValue("Location").orElseThrow())
        .contains(ORIGIN + "/lojas?mercadoPago=expirado");
    assertThat(TOKEN_REQUESTS).hasSize(1);

    String expired = startAuthorization(a, tenantA).get("state");
    jdbc.update(
        "UPDATE payment_oauth_state SET created_at=now()-interval '20 minutes', "
            + "expires_at=now()-interval '10 minutes' WHERE used_at IS NULL");
    assertThat(a.get(callback("TG-code", expired)).headers().firstValue("Location").orElseThrow())
        .contains(ORIGIN + "/lojas?mercadoPago=expirado");
    assertThat(TOKEN_REQUESTS).hasSize(1);

    // A newer attempt replaces the pending one.
    String first = startAuthorization(a, tenantA).get("state");
    String second = startAuthorization(a, tenantA).get("state");
    assertThat(a.get(callback("TG-code", first)).headers().firstValue("Location").orElseThrow())
        .contains("mercadoPago=expirado");
    assertThat(a.get(callback("TG-code", second)).headers().firstValue("Location").orElseThrow())
        .contains("mercadoPago=conectado");
  }

  @Test
  void deniedAuthorizationAndProviderFailuresKeepTheStoreDisconnected() throws Exception {
    Browser a = login("payments-a@example.test");
    String denied = startAuthorization(a, tenantA).get("state");
    var response = a.get("/api/payments/mercado-pago/callback?error=access_denied&state=" + denied);
    assertThat(response.headers().firstValue("Location").orElseThrow())
        .contains("/lojas/" + tenantA + "/pagamentos?mercadoPago=negado");
    assertThat(TOKEN_REQUESTS).isEmpty();

    for (Reply failure :
        List.of(
            new Reply(400, "{\"error\":\"invalid_grant\",\"message\":\"code TG-code used\"}"),
            new Reply(500, "upstream down"),
            new Reply(200, "{\"access_token\":\"APP_USR-x\"}"),
            new Reply(200, "[]"))) {
      NEXT.set(failure);
      String state = startAuthorization(a, tenantA).get("state");
      assertThat(a.get(callback("TG-code", state)).headers().firstValue("Location").orElseThrow())
          .contains("/lojas/" + tenantA + "/pagamentos?mercadoPago=erro");
    }
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("status").asString())
        .isEqualTo("NOT_CONNECTED");
  }

  @Test
  void anotherOwnerCannotReadStartOrDisconnectTheConnection() throws Exception {
    Browser a = login("payments-a@example.test");
    connect(a, tenantA);
    Browser b = login("payments-b@example.test");
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(b.post(path(tenantA) + "/authorization").statusCode()).isEqualTo(404);
    assertThat(b.delete(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(b.get(path(tenantA)).body()).doesNotContain("987654");
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("status").asString())
        .isEqualTo("CONNECTED");
    assertThat(JSON.readTree(b.get(path(tenantB)).body()).get("status").asString())
        .isEqualTo("NOT_CONNECTED");
    // Mutations keep their CSRF protection; anonymous reads are rejected.
    assertThat(a.send("POST", path(tenantA) + "/authorization", null, null).statusCode())
        .isEqualTo(403);
    assertThat(a.send("DELETE", path(tenantA), null, null).statusCode()).isEqualTo(403);
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);
    assertThat(new Browser().get(callback("TG-code", Pkce.randomValue())).statusCode())
        .isEqualTo(401);
  }

  @Test
  void ownerDisconnectsAndTokensAreErased() throws Exception {
    Browser a = login("payments-a@example.test");
    connect(a, tenantA);
    var disconnected = a.delete(path(tenantA));
    assertThat(disconnected.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(disconnected.body()).get("status").asString())
        .isEqualTo("NOT_CONNECTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT access_token IS NULL AND refresh_token IS NULL FROM payment_account WHERE tenant_id=?",
                Boolean.class,
                tenantA))
        .isTrue();
    assertThatThrownBy(() -> connections.accessToken(tenantA))
        .isInstanceOf(PaymentException.class)
        .hasFieldOrPropertyWithValue("reason", PaymentException.Reason.NOT_CONNECTED);
    // Reconnecting works after a disconnection.
    connect(a, tenantA);
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("status").asString())
        .isEqualTo("CONNECTED");
  }

  @Test
  void refreshRotatesTheRefreshTokenAndRevocationErasesTheConnection(CapturedOutput output)
      throws Exception {
    Browser a = login("payments-a@example.test");
    connect(a, tenantA);
    assertThat(connections.accessToken(tenantA)).isEqualTo("APP_USR-access-1");
    assertThat(TOKEN_REQUESTS).hasSize(1);

    jdbc.update(
        "UPDATE payment_account SET access_expires_at=now()+interval '2 days' WHERE tenant_id=?",
        tenantA);
    NEXT.set(tokens("APP_USR-access-2", "TG-refresh-2", 15552000));
    assertThat(connections.accessToken(tenantA)).isEqualTo("APP_USR-access-2");
    JsonNode refresh = TOKEN_REQUESTS.getLast();
    assertThat(refresh.get("grant_type").asString()).isEqualTo("refresh_token");
    assertThat(refresh.get("refresh_token").asString()).isEqualTo("TG-refresh-1");
    // The rotated pair is stored: the next use needs no refresh and the next refresh sends the
    // new refresh token.
    assertThat(connections.accessToken(tenantA)).isEqualTo("APP_USR-access-2");
    assertThat(TOKEN_REQUESTS).hasSize(2);
    jdbc.update(
        "UPDATE payment_account SET access_expires_at=now()+interval '1 day' WHERE tenant_id=?",
        tenantA);
    NEXT.set(tokens("APP_USR-access-3", "TG-refresh-3", 15552000));
    assertThat(connections.refreshDue(10)).isEqualTo(1);
    assertThat(TOKEN_REQUESTS.getLast().get("refresh_token").asString()).isEqualTo("TG-refresh-2");
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("refreshedAt").isNull()).isFalse();

    // A provider failure keeps the connection and the current tokens.
    jdbc.update(
        "UPDATE payment_account SET access_expires_at=now()+interval '1 day' WHERE tenant_id=?",
        tenantA);
    NEXT.set(new Reply(500, "{}"));
    assertThatThrownBy(() -> connections.accessToken(tenantA))
        .hasFieldOrPropertyWithValue("reason", PaymentException.Reason.PROVIDER_ERROR);
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("status").asString())
        .isEqualTo("CONNECTED");

    // invalid_grant: the seller revoked the application.
    NEXT.set(new Reply(400, "{\"error\":\"invalid_grant\"}"));
    assertThatThrownBy(() -> connections.accessToken(tenantA))
        .hasFieldOrPropertyWithValue("reason", PaymentException.Reason.REVOKED);
    JsonNode revoked = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(revoked.get("status").asString()).isEqualTo("REVOKED");
    assertThat(revoked.get("accountId").asString()).isEqualTo("987654");
    assertThat(
            jdbc.queryForObject(
                "SELECT access_token IS NULL FROM payment_account WHERE tenant_id=?",
                Boolean.class,
                tenantA))
        .isTrue();
    assertThat(output.getAll()).doesNotContain("APP_USR-access", "TG-refresh", CLIENT_SECRET);
  }

  @Test
  void anExpiredAccessTokenIsReportedAsExpired() throws Exception {
    Browser a = login("payments-a@example.test");
    connect(a, tenantA);
    jdbc.update(
        "UPDATE payment_account SET access_expires_at=now()-interval '1 minute' WHERE tenant_id=?",
        tenantA);
    assertThat(JSON.readTree(a.get(path(tenantA)).body()).get("status").asString())
        .isEqualTo("EXPIRED");
    assertThat(providers.available()).isTrue();
  }

  private void connect(Browser browser, UUID tenant) throws Exception {
    String state = startAuthorization(browser, tenant).get("state");
    assertThat(
            browser.get(callback("TG-code", state)).headers().firstValue("Location").orElseThrow())
        .contains("mercadoPago=conectado");
  }

  private Map<String, String> startAuthorization(Browser browser, UUID tenant) throws Exception {
    var response = browser.post(path(tenant) + "/authorization");
    assertThat(response.statusCode()).isEqualTo(200);
    String url = JSON.readTree(response.body()).get("authorizationUrl").asString();
    assertThat(url).startsWith("https://auth.mercadopago.com/authorization?");
    Map<String, String> query = new HashMap<>();
    for (String pair : URI.create(url).getRawQuery().split("&")) {
      String[] parts = pair.split("=", 2);
      query.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    return query;
  }

  private static Reply tokens(String access, String refresh, long expiresIn) {
    return new Reply(
        200,
        "{\"access_token\":\""
            + access
            + "\",\"token_type\":\"Bearer\",\"expires_in\":"
            + expiresIn
            + ",\"scope\":\"offline_access read write\",\"user_id\":987654,\"refresh_token\":\""
            + refresh
            + "\",\"public_key\":\"APP_USR-public\",\"live_mode\":false}");
  }

  private static String callback(String code, String state) {
    return "/api/payments/mercado-pago/callback?code="
        + code
        + "&state="
        + java.net.URLEncoder.encode(state, StandardCharsets.UTF_8);
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/payments/mercado-pago";
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

    HttpResponse<String> post(String path) throws Exception {
      return send("POST", path, null, csrfToken);
    }

    HttpResponse<String> delete(String path) throws Exception {
      return send("DELETE", path, null, csrfToken);
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
