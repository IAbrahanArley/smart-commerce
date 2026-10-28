package com.smartcommerce.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * SC-043 over real HTTP and PostgreSQL: the store OWNER manages home banners (limit, order, images,
 * period, internal links) and sections, marks featured products, and the public storefront shows
 * only what the published store has active. Another store's owner gets one 404 for all of it.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeContentIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic home secret 2026!";
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
    tenantA = store(activeIdentity("home-a@example.test"), "home-a");
    tenantB = store(activeIdentity("home-b@example.test"), "home-b");
  }

  @Test
  void ownerManagesBannersAndOnlyActiveOnesArePublic() throws Exception {
    Browser a = login("home-a@example.test");
    String banners = "/api/tenants/" + tenantA + "/content/banners";
    var created =
        a.send(
            "POST",
            banners,
            Map.of(
                "alt", "Coleção de inverno",
                "title", "Inverno",
                "linkKind", "category",
                "linkTarget", "casacos"));
    assertThat(created.statusCode()).isEqualTo(200);
    UUID first = UUID.fromString(JSON.readTree(created.body()).get(0).get("id").asText());
    // Alternative text is required; links are internal only; the period must be ordered.
    assertThat(a.send("POST", banners, Map.of("alt", " ")).statusCode()).isEqualTo(400);
    assertThat(
            a.send(
                    "POST",
                    banners,
                    Map.of("alt", "X", "linkKind", "PRODUCT", "linkTarget", "https://evil.test"))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            a.send(
                    "POST",
                    banners,
                    Map.of(
                        "alt",
                        "X",
                        "startsAt",
                        "2026-12-01T00:00:00Z",
                        "endsAt",
                        "2026-11-01T00:00:00Z"))
                .statusCode())
        .isEqualTo(400);
    UUID second = null;
    for (int i = 0; i < 4; i++) {
      var response =
          a.send(
              "POST",
              banners,
              Map.of("alt", "Banner " + i, "linkKind", "SEARCH", "linkTarget", "tênis"));
      assertThat(response.statusCode()).isEqualTo(200);
      if (i == 0)
        second = UUID.fromString(JSON.readTree(response.body()).get(1).get("id").asText());
    }
    var limit = a.send("POST", banners, Map.of("alt", "Sexto"));
    assertThat(limit.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(limit.body()).get("code").asText()).isEqualTo("BANNER_LIMIT");

    // The order must list every banner once.
    List<UUID> ids = ids(a.get(banners));
    assertThat(
            a.send("PUT", banners + "/order", Map.of("bannerIds", ids.subList(0, 2))).statusCode())
        .isEqualTo(400);
    List<UUID> reversed = new java.util.ArrayList<>(ids);
    java.util.Collections.reverse(reversed);
    assertThat(ids(a.send("PUT", banners + "/order", Map.of("bannerIds", reversed))))
        .isEqualTo(reversed);
    assertThat(ids(a.send("DELETE", banners + "/" + reversed.get(0), null))).hasSize(4);

    // Images: desktop then mobile; a non-image is a 400.
    assertThat(upload(a, first, "desktop", png(1200, 400)).statusCode()).isEqualTo(200);
    assertThat(upload(a, first, "mobile", png(600, 600)).statusCode()).isEqualTo(200);
    assertThat(upload(a, first, "tablet", png(600, 600)).statusCode()).isEqualTo(400);
    assertThat(
            upload(a, second, "desktop", "not an image".getBytes(StandardCharsets.UTF_8))
                .statusCode())
        .isEqualTo(400);
    JsonNode listed = JSON.readTree(a.get(banners).body());
    JsonNode firstView = find(listed, first);
    assertThat(firstView.get("desktopImage").asBoolean()).isTrue();
    assertThat(firstView.get("mobileImage").asBoolean()).isTrue();
    assertThat(a.get(banners + "/" + first + "/images/desktop").statusCode()).isEqualTo(200);

    // Another tenant: one 404 for list, edit, image and upload.
    Browser b = login("home-b@example.test");
    assertThat(b.get(banners).statusCode()).isEqualTo(404);
    assertThat(b.send("PUT", banners + "/" + first, Map.of("alt", "Invasor")).statusCode())
        .isEqualTo(404);
    assertThat(b.send("DELETE", banners + "/" + first, null).statusCode()).isEqualTo(404);
    assertThat(upload(b, first, "desktop", png(1200, 400)).statusCode()).isEqualTo(404);
    String foreign = "/api/tenants/" + tenantB + "/content/banners/" + first;
    assertThat(b.send("PUT", foreign, Map.of("alt", "Invasor")).statusCode()).isEqualTo(404);
    assertThat(b.get(foreign + "/images/desktop").statusCode()).isEqualTo(404);

    // Public: only after publishing, only banners with a desktop image inside their period.
    Browser buyer = new Browser();
    String image = "/api/public/stores/home-a/banners/" + first + "/images/desktop";
    assertThat(buyer.get(image).statusCode()).isEqualTo(404);
    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id IN (?,?)", tenantA, tenantB);
    JsonNode catalog = JSON.readTree(buyer.get("/api/public/stores/home-a/catalog").body());
    assertThat(catalog.get("banners")).hasSize(1);
    JsonNode banner = catalog.get("banners").get(0);
    assertThat(banner.get("alt").asText()).isEqualTo("Coleção de inverno");
    assertThat(banner.get("linkKind").asText()).isEqualTo("CATEGORY");
    assertThat(banner.get("linkTarget").asText()).isEqualTo("casacos");
    assertThat(banner.get("mobileImage").asBoolean()).isTrue();
    var served = buyer.bytes(image);
    assertThat(served.statusCode()).isEqualTo(200);
    assertThat(served.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(served.body())).getWidth())
        .isEqualTo(1200);
    assertThat(
            buyer
                .get("/api/public/stores/home-a/banners/" + second + "/images/desktop")
                .statusCode())
        .isEqualTo(404);
    assertThat(
            buyer
                .get("/api/public/stores/home-b/banners/" + first + "/images/desktop")
                .statusCode())
        .isEqualTo(404);

    // A period that already ended hides the banner and its image.
    var ended =
        a.send(
            "PUT",
            banners + "/" + first,
            Map.of(
                "alt",
                "Coleção de inverno",
                "startsAt",
                Instant.now().minusSeconds(7200).toString(),
                "endsAt",
                Instant.now().minusSeconds(3600).toString()));
    assertThat(ended.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(buyer.get("/api/public/stores/home-a/catalog").body()).get("banners"))
        .isEmpty();
    assertThat(buyer.get(image).statusCode()).isEqualTo(404);
  }

  @Test
  void sectionsAndFeaturedProductsShapeTheHome() throws Exception {
    UUID shoes = category(tenantA, "Calçados", "calcados", true);
    UUID hidden = category(tenantA, "Rascunho", "rascunho", false);
    UUID foreign = category(tenantB, "Alheia", "alheia", true);
    UUID featured = product(tenantA, "Tênis destaque", true);
    product(tenantA, "Meia comum", true);

    Browser a = login("home-a@example.test");
    String sections = "/api/tenants/" + tenantA + "/content/sections";
    assertThat(JSON.readTree(a.get(sections).body())).isEmpty();
    var saved =
        a.send(
            "PUT",
            sections,
            Map.of(
                "sections",
                List.of(
                    Map.of("kind", "FEATURED", "title", "Destaques"),
                    Map.of("kind", "category", "title", "Calçados", "categoryId", shoes),
                    Map.of("kind", "CATEGORY", "title", "Rascunho", "categoryId", hidden),
                    Map.of("kind", "NEW", "title", "Lançamentos"))));
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(saved.body())).hasSize(4);
    assertThat(
            a.send(
                    "PUT",
                    sections,
                    Map.of(
                        "sections",
                        List.of(Map.of("kind", "NEW", "title", "X", "categoryId", shoes))))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            a.send(
                    "PUT",
                    sections,
                    Map.of(
                        "sections",
                        List.of(Map.of("kind", "CATEGORY", "title", "X", "categoryId", foreign))))
                .statusCode())
        .isEqualTo(404);
    assertThat(JSON.readTree(a.get(sections).body())).hasSize(4);

    Browser b = login("home-b@example.test");
    assertThat(b.get(sections).statusCode()).isEqualTo(404);
    assertThat(b.send("PUT", sections, Map.of("sections", List.of())).statusCode()).isEqualTo(404);

    // Featured: set through the product storefront card; other fields untouched.
    var marked =
        a.send(
            "PUT",
            "/api/tenants/" + tenantA + "/products/" + featured + "/storefront",
            Map.of("featured", true));
    assertThat(marked.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(marked.body()).get("featured").asBoolean()).isTrue();
    assertThat(
            b.send(
                    "PUT",
                    "/api/tenants/" + tenantA + "/products/" + featured + "/storefront",
                    Map.of("featured", false))
                .statusCode())
        .isEqualTo(404);

    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id IN (?,?)", tenantA, tenantB);
    Browser buyer = new Browser();
    JsonNode home = JSON.readTree(buyer.get("/api/public/stores/home-a/catalog").body());
    // The section of the unpublished category is left out.
    assertThat(home.get("sections")).hasSize(3);
    assertThat(home.get("sections").get(1).get("categorySlug").asText()).isEqualTo("calcados");
    JsonNode onlyFeatured =
        JSON.readTree(buyer.get("/api/public/stores/home-a/catalog?destaque=1").body());
    assertThat(onlyFeatured.get("total").asLong()).isEqualTo(1);
    assertThat(onlyFeatured.get("products").get(0).get("name").asText())
        .isEqualTo("Tênis destaque");
    assertThat(buyer.get("/api/public/stores/home-a/catalog?destaque=sim").statusCode())
        .isEqualTo(400);
    assertThat(JSON.readTree(buyer.get("/api/public/stores/home-b/catalog").body()).get("sections"))
        .isEmpty();
  }

  // --- helpers -----------------------------------------------------------------------------

  private static List<UUID> ids(HttpResponse<String> response) throws Exception {
    assertThat(response.statusCode()).isEqualTo(200);
    List<UUID> ids = new java.util.ArrayList<>();
    for (JsonNode banner : JSON.readTree(response.body()))
      ids.add(UUID.fromString(banner.get("id").asText()));
    return ids;
  }

  private static JsonNode find(JsonNode banners, UUID id) {
    for (JsonNode banner : banners)
      if (banner.get("id").asText().equals(id.toString())) return banner;
    throw new AssertionError("Banner not listed");
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < width; x += 10) image.setRGB(x, height / 2, 0x3366cc);
    var out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private HttpResponse<String> upload(Browser owner, UUID banner, String variant, byte[] file)
      throws Exception {
    String boundary = "SyntheticBoundary2026";
    var bytes = new ByteArrayOutputStream();
    bytes.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"banner.png\""
                + "\r\nContent-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    bytes.write(file);
    bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/tenants/"
                        + owner.tenant
                        + "/content/banners/"
                        + banner
                        + "/images/"
                        + variant))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("X-CSRF-TOKEN", owner.csrfToken);
    return owner.client.send(
        request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private UUID category(UUID tenant, String name, String slug, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_category(id,tenant_id,store_id,parent_id,name,name_key,slug,position,published,created_at)
        SELECT ?,?,id,NULL,?,lower(?),?,0,?,now() FROM store WHERE tenant_id=?
        """,
        id,
        tenant,
        name,
        name,
        slug,
        published,
        tenant);
    return id;
  }

  private UUID product(UUID tenant, String name, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind,slug) SELECT ?,?,id,?,?,10.00,'BRL',now(),now(),?,'SIMPLE',? FROM store WHERE tenant_id=?",
        id,
        tenant,
        name,
        "SKU-" + id.toString().substring(0, 8),
        published,
        "p-" + id.toString().substring(0, 8),
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,10.00,NULL,'BRL','[]',?)",
        id,
        tenant,
        id,
        "SKU-" + id.toString().substring(0, 8),
        id.toString());
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
    browser.tenant = email.endsWith("-b@example.test") ? tenantB : tenantA;
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
    UUID tenant;

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asText();
    }

    HttpResponse<String> get(String path) throws Exception {
      return raw("GET", path, null, null);
    }

    HttpResponse<byte[]> bytes(String path) throws Exception {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .header("Accept", "image/*")
              .timeout(Duration.ofSeconds(20))
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
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
