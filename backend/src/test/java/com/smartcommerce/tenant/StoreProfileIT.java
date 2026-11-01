package com.smartcommerce.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
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
 * MVP block 1b over real HTTP and PostgreSQL: the OWNER edits name, contacts and logo with
 * optimistic versions; another store's owner gets one 404; the storefront shows contacts and logo
 * only while the store is published.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StoreProfileIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic store profile secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    tenantA = store(activeIdentity("perfil-a@example.test"), "perfil-a");
    tenantB = store(activeIdentity("perfil-b@example.test"), "perfil-b");
  }

  @Test
  void ownerEditsNameAndContactsWithVersionsAndOthersGetOne404() throws Exception {
    Browser a = login("perfil-a@example.test");
    JsonNode empty = JSON.readTree(a.get(path(tenantA)).body());
    assertThat(empty.get("name").asText()).isEqualTo("Synthetic store");
    assertThat(empty.get("version").asLong()).isZero();
    assertThat(empty.get("hasLogo").asBoolean()).isFalse();

    var saved =
        a.put(
            path(tenantA),
            profile("  Loja da Ana  ", "contato@ana.example", "(11) 98888-7777", "@Loja.Ana", 0));
    assertThat(saved.statusCode()).isEqualTo(200);
    JsonNode body = JSON.readTree(saved.body());
    assertThat(body.get("name").asText()).isEqualTo("Loja da Ana");
    assertThat(body.get("whatsapp").asText()).isEqualTo("5511988887777");
    assertThat(body.get("instagram").asText()).isEqualTo("loja.ana");
    assertThat(body.get("version").asLong()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT name FROM store WHERE tenant_id=?", String.class, tenantA))
        .isEqualTo("Loja da Ana");

    // Stale version: a conflict, nothing changes. Blank contacts stop being shown.
    assertThat(a.put(path(tenantA), profile("Outra", null, null, null, 0)).statusCode())
        .isEqualTo(409);
    var cleared = a.put(path(tenantA), profile("Loja da Ana", "", "", "", 1));
    assertThat(JSON.readTree(cleared.body()).get("contactEmail").isNull()).isTrue();
    assertThat(JSON.readTree(cleared.body()).get("version").asLong()).isEqualTo(2);

    // Invalid values: 400 with a readable message, nothing saved.
    var invalid = a.put(path(tenantA), profile("Loja", "sem-arroba", null, null, 2));
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(invalid.body()).contains("WhatsApp com DDD");

    // B's owner, or A on B's store: one 404, never B's data.
    Browser b = login("perfil-b@example.test");
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
    assertThat(b.put(path(tenantA), profile("Invasão", null, null, null, 2)).statusCode())
        .isEqualTo(404);
    assertThat(a.get(path(tenantB)).statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject("SELECT name FROM store WHERE tenant_id=?", String.class, tenantA))
        .isEqualTo("Loja da Ana");
    // CSRF protects writes.
    assertThat(a.send("PUT", path(tenantA), "{}", null, null).statusCode()).isEqualTo(403);
  }

  @Test
  void logoIsNormalizedReplacedAndRemovedAndOnlyPublishedStoresShowIt() throws Exception {
    Browser a = login("perfil-a@example.test");
    a.put(
        path(tenantA), profile("Loja da Ana", "contato@ana.example", "11988887777", "loja.ana", 0));

    // Not an image, or a wrong version: refused.
    assertThat(upload(a, 1, "não é imagem".getBytes(), "logo.png", "image/png").statusCode())
        .isEqualTo(400);
    var stale = upload(a, 0, png(), "logo.png", "image/png");
    assertThat(stale.statusCode()).isEqualTo(409);
    var uploaded = upload(a, 1, png(), "logo.png", "image/png");
    assertThat(uploaded.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(uploaded.body()).get("hasLogo").asBoolean()).isTrue();
    var preview = a.bytes(path(tenantA) + "/logo");
    assertThat(preview.statusCode()).isEqualTo(200);
    assertThat(preview.headers().firstValue("Content-Type").orElse("")).isEqualTo("image/png");
    var stored = ImageIO.read(new ByteArrayInputStream(preview.body()));
    assertThat(stored.getWidth()).isEqualTo(240);
    // The size goes with the profile, so pages reserve the logo's space before it loads.
    JsonNode owned = JSON.readTree(uploaded.body());
    assertThat(owned.get("logoWidth").asInt()).isEqualTo(stored.getWidth());
    assertThat(owned.get("logoHeight").asInt()).isEqualTo(stored.getHeight());
    Browser b = login("perfil-b@example.test");
    assertThat(b.bytes(path(tenantA) + "/logo").statusCode()).isEqualTo(404);

    // Draft store: the storefront shows nothing at all.
    Browser buyer = new Browser();
    assertThat(buyer.get("/api/public/stores/perfil-a").statusCode()).isEqualTo(404);
    assertThat(buyer.bytes("/api/public/stores/perfil-a/logo").statusCode()).isEqualTo(404);

    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id=?", tenantA);
    JsonNode store = JSON.readTree(buyer.get("/api/public/stores/perfil-a").body());
    assertThat(store.get("name").asText()).isEqualTo("Loja da Ana");
    JsonNode profile = store.get("profile");
    assertThat(profile.get("contactEmail").asText()).isEqualTo("contato@ana.example");
    assertThat(profile.get("whatsapp").asText()).isEqualTo("5511988887777");
    assertThat(profile.get("instagram").asText()).isEqualTo("loja.ana");
    assertThat(profile.get("logo").asBoolean()).isTrue();
    assertThat(profile.get("logoWidth").asInt()).isEqualTo(stored.getWidth());
    assertThat(profile.get("logoHeight").asInt()).isEqualTo(stored.getHeight());
    var logo = buyer.bytes("/api/public/stores/perfil-a/logo");
    assertThat(logo.statusCode()).isEqualTo(200);
    assertThat(logo.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");

    var removed = a.send("DELETE", path(tenantA) + "/logo?version=2", null, a.csrfToken, null);
    assertThat(removed.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(removed.body()).get("hasLogo").asBoolean()).isFalse();
    assertThat(buyer.bytes("/api/public/stores/perfil-a/logo").statusCode()).isEqualTo(404);
    assertThat(
            JSON.readTree(buyer.get("/api/public/stores/perfil-a").body())
                .get("profile")
                .get("logo")
                .asBoolean())
        .isFalse();
    // A store that never saved a profile still answers, with no contacts.
    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id=?", tenantB);
    JsonNode bare = JSON.readTree(buyer.get("/api/public/stores/perfil-b").body()).get("profile");
    assertThat(bare.get("contactEmail").isNull()).isTrue();
    assertThat(bare.get("logo").asBoolean()).isFalse();
    assertThat(bare.get("logoWidth").isNull()).isTrue();
  }

  // --- helpers -----------------------------------------------------------------------------

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/profile";
  }

  private static Map<String, Object> profile(
      String name, String email, String whatsapp, String instagram, long version) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("contactEmail", email);
    body.put("whatsapp", whatsapp);
    body.put("instagram", instagram);
    body.put("expectedVersion", version);
    return body;
  }

  private static byte[] png() throws Exception {
    var image = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private HttpResponse<String> upload(
      Browser owner, long version, byte[] file, String filename, String type) throws Exception {
    String boundary = "SyntheticBoundary2026";
    var bytes = new ByteArrayOutputStream();
    bytes.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"version\"\r\n\r\n"
                + version
                + "\r\n--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename
                + "\"\r\nContent-Type: "
                + type
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    bytes.write(file);
    bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return owner.client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path(tenantA) + "/logo"))
            .timeout(Duration.ofSeconds(20))
            .header("X-CSRF-TOKEN", owner.csrfToken)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
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
                    browser.csrfToken,
                    null)
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
      return send("GET", path, null, null, null);
    }

    HttpResponse<String> put(String path, Object body) throws Exception {
      return send("PUT", path, JSON.writeValueAsString(body), csrfToken, null);
    }

    HttpResponse<byte[]> bytes(String path) throws Exception {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(20))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
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
}
