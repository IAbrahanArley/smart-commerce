package com.smartcommerce.catalog;

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

/** Session, CSRF, monetary contracts and tenant isolation against a disposable PostgreSQL. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductImagesIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic inventory integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID ownerA;
  UUID ownerB;
  UUID tenantA;
  UUID tenantB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    ownerA = activeIdentity("catalog-a@example.test");
    ownerB = activeIdentity("catalog-b@example.test");
    tenantA = store(ownerA, "catalog-a");
    tenantB = store(ownerB, "catalog-b");
  }

  @Test
  void imagesRequireOwnershipAndBothPublicationFlagsEvenAtAnOldUrl() throws Exception {
    Browser a = login("catalog-a@example.test"), b = login("catalog-b@example.test");
    UUID product = create(a, tenantA, "PHOTO");
    String base = path(tenantA) + "/" + product;
    var uploaded =
        upload(
            a,
            tenantA,
            product,
            0,
            ImageNormalizerTest.picture("png", 300, 400),
            "image.png",
            "image/png",
            a.csrfToken,
            null);
    assertThat(uploaded.statusCode()).isEqualTo(200);
    JsonNode body = JSON.readTree(uploaded.body());
    assertThat(body.get("version").asLong()).isEqualTo(1);
    String image = body.get("images").get(0).get("id").asText();
    assertThat(b.get(base + "/images").statusCode()).isEqualTo(404);
    assertThat(b.get(base + "/images/" + image + "/content").statusCode()).isEqualTo(404);
    assertThat(
            upload(
                    b,
                    tenantA,
                    product,
                    1,
                    ImageNormalizerTest.picture("png", 300, 300),
                    "image.png",
                    "image/png",
                    b.csrfToken,
                    null)
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.put(base + "/images/order", Map.of("version", 1, "imageIds", List.of(image)))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.send("DELETE", base + "/images/" + image + "?version=1", null, b.csrfToken, null)
                .statusCode())
        .isEqualTo(404);
    JsonNode untouched = JSON.readTree(a.get(base + "/images").body());
    assertThat(untouched.get("images").size()).isEqualTo(1);
    assertThat(untouched.get("version").asLong()).isEqualTo(1);
    Browser visitor = new Browser();
    String publicPath = "/api/public/stores/catalog-a/products/" + product + "/images";
    assertThat(visitor.get(publicPath).statusCode()).isEqualTo(404);
    assertThat(
            a.put("/api/tenants/" + tenantA + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(publish(a, tenantA, product, true, 1).statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(visitor.get(publicPath).body()).get("images").size()).isEqualTo(1);
    var content =
        visitor.client.send(
            HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + publicPath + "/" + image + "/content"))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(content.statusCode()).isEqualTo(200);
    assertThat(content.headers().firstValue("content-type").orElse("")).contains("image/png");
    assertThat(content.headers().firstValue("cache-control").orElse("")).contains("no-store");
    assertThat(content.headers().allValues("set-cookie")).isEmpty();
    assertThat(
            javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(content.body())).getWidth())
        .isEqualTo(300);
    assertThat(visitor.get(publicPath + "/primary/content").statusCode()).isEqualTo(200);
    assertThat(publish(a, tenantA, product, false, 2).statusCode()).isEqualTo(200);
    assertThat(visitor.get(publicPath + "/" + image + "/content").statusCode()).isEqualTo(404);
    assertThat(a.get(base + "/images/" + image + "/content").statusCode()).isEqualTo(200);
  }

  @Test
  void missingImagesStay404WithTheStorefrontProxyImageAcceptHeader() throws Exception {
    Browser a = login("catalog-a@example.test"), b = login("catalog-b@example.test");
    UUID product = create(a, tenantA, "PROXY-ACCEPT");
    var uploaded =
        upload(
            a,
            tenantA,
            product,
            0,
            ImageNormalizerTest.picture("png", 300, 300),
            "image.png",
            "image/png",
            a.csrfToken,
            null);
    String image = JSON.readTree(uploaded.body()).get("images").get(0).get("id").asText();
    assertThat(
            a.put("/api/tenants/" + tenantA + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(publish(a, tenantA, product, true, 1).statusCode()).isEqualTo(200);
    String publicPath = "/api/public/stores/catalog-a/products/" + product + "/images";
    var served = imageRequest(null, publicPath + "/" + image + "/content");
    assertThat(served.statusCode()).isEqualTo(200);
    assertThat(served.headers().firstValue("content-type").orElse("")).contains("image/png");
    for (String missing :
        List.of(
            publicPath + "/" + UUID.randomUUID() + "/content",
            "/api/public/stores/catalog-missing/products/" + product + "/images/primary/content",
            "/api/public/stores/catalog-a/products/"
                + UUID.randomUUID()
                + "/images/primary/content",
            "/api/public/stores/catalog-b/products/" + product + "/images/" + image + "/content")) {
      var response = imageRequest(null, missing);
      assertThat(response.statusCode()).as(missing).isEqualTo(404);
      assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
      assertThat(response.headers().allValues("set-cookie")).isEmpty();
    }
    assertThat(
            imageRequest(b, path(tenantA) + "/" + product + "/images/" + image + "/content")
                .statusCode())
        .isEqualTo(404);
    assertThat(publish(a, tenantA, product, false, 2).statusCode()).isEqualTo(200);
    assertThat(imageRequest(null, publicPath + "/" + image + "/content").statusCode())
        .isEqualTo(404);
    assertThat(imageRequest(null, publicPath + "/primary/content").statusCode()).isEqualTo(404);
  }

  /** Mirrors the Accept header sent by storefront-web's public image proxy. */
  private HttpResponse<byte[]> imageRequest(Browser owner, String route) throws Exception {
    var client = owner == null ? new Browser().client : owner.client;
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route))
            .header("Accept", "image/jpeg, image/png")
            .timeout(Duration.ofSeconds(30))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  @Test
  void uploadValidatesCsrfOriginFileContentAndLimitsWithoutCreatingRows() throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = create(a, tenantA, "INVALID");
    byte[] png = ImageNormalizerTest.picture("png", 300, 300);
    assertThat(
            upload(a, tenantA, product, 0, png, "image.png", "image/png", null, null).statusCode())
        .isEqualTo(403);
    assertThat(
            upload(
                    a,
                    tenantA,
                    product,
                    0,
                    png,
                    "image.png",
                    "image/png",
                    a.csrfToken,
                    "https://foreign.example")
                .statusCode())
        .isEqualTo(403);
    assertThat(
            upload(
                    a,
                    tenantA,
                    product,
                    0,
                    "<svg onload='alert(1)'/>".getBytes(),
                    "image.png",
                    "image/png",
                    a.csrfToken,
                    null)
                .statusCode())
        .isEqualTo(400);
    assertThat(
            upload(a, tenantA, product, 0, png, "image.jpg", "image/jpeg", a.csrfToken, null)
                .statusCode())
        .isEqualTo(400);
    assertThat(
            upload(
                    a,
                    tenantA,
                    product,
                    0,
                    new byte[5 * 1024 * 1024 + 1],
                    "image.png",
                    "image/png",
                    a.csrfToken,
                    null)
                .statusCode())
        .isEqualTo(413);
    JsonNode state = JSON.readTree(a.get(path(tenantA) + "/" + product + "/images").body());
    assertThat(state.get("images").size()).isZero();
    assertThat(state.get("version").asLong()).isZero();
  }

  @Test
  void concurrentLastSlotIsSerializedAndReorderingAndRemovalRequireFreshVersions()
      throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID product = create(a, tenantA, "CONCURRENT");
    byte[] png = ImageNormalizerTest.picture("png", 300, 300);
    for (int i = 0; i < 5; i++)
      assertThat(
              upload(a, tenantA, product, i, png, "image.png", "image/png", a.csrfToken, null)
                  .statusCode())
          .isEqualTo(200);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () ->
                  upload(a, tenantA, product, 5, png, "image.png", "image/png", a.csrfToken, null)
                      .statusCode());
      var second =
          executor.submit(
              () ->
                  upload(a, tenantA, product, 5, png, "image.png", "image/png", a.csrfToken, null)
                      .statusCode());
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
    }
    String base = path(tenantA) + "/" + product;
    JsonNode state = JSON.readTree(a.get(base + "/images").body());
    assertThat(state.get("images").size()).isEqualTo(6);
    assertThat(
            upload(a, tenantA, product, 6, png, "image.png", "image/png", a.csrfToken, null)
                .statusCode())
        .isEqualTo(409);
    List<String> order = new java.util.ArrayList<>();
    for (JsonNode item : state.get("images")) order.add(item.get("id").asText());
    java.util.Collections.reverse(order);
    assertThat(a.put(base + "/images/order", Map.of("version", 6, "imageIds", order)).statusCode())
        .isEqualTo(200);
    assertThat(a.put(base + "/images/order", Map.of("version", 6, "imageIds", order)).statusCode())
        .isEqualTo(409);
    assertThat(
            JSON.readTree(a.get(base + "/images").body()).get("images").get(0).get("id").asText())
        .isEqualTo(order.getFirst());
    assertThat(
            a.send(
                    "DELETE",
                    base + "/images/" + UUID.randomUUID() + "?version=7",
                    null,
                    a.csrfToken,
                    null)
                .statusCode())
        .isEqualTo(404);
    assertThat(
            a.send(
                    "DELETE",
                    base + "/images/" + order.getFirst() + "?version=7",
                    null,
                    a.csrfToken,
                    null)
                .statusCode())
        .isEqualTo(200);
    state = JSON.readTree(a.get(base + "/images").body());
    assertThat(state.get("images").size()).isEqualTo(5);
    assertThat(state.get("version").asLong()).isEqualTo(8);
  }

  @Test
  void productListAndDetailShowTheCoverFollowingOrderAndRemoval() throws Exception {
    Browser a = login("catalog-a@example.test");
    UUID withoutPhotos = create(a, tenantA, "NO-PHOTO");
    UUID product = create(a, tenantA, "COVER");
    byte[] png = ImageNormalizerTest.picture("png", 300, 300);
    for (int i = 0; i < 2; i++)
      assertThat(
              upload(a, tenantA, product, i, png, "image.png", "image/png", a.csrfToken, null)
                  .statusCode())
          .isEqualTo(200);
    String base = path(tenantA) + "/" + product;
    List<String> ids = new java.util.ArrayList<>();
    for (JsonNode item : JSON.readTree(a.get(base + "/images").body()).get("images"))
      ids.add(item.get("id").asText());

    JsonNode list = JSON.readTree(a.get(path(tenantA)).body()).get("products");
    JsonNode bare = find(list, withoutPhotos);
    assertThat(bare.get("coverImageId").isNull()).isTrue();
    assertThat(bare.get("imageCount").asInt()).isZero();
    JsonNode covered = find(list, product);
    assertThat(covered.get("coverImageId").asText()).isEqualTo(ids.getFirst());
    assertThat(covered.get("coverImageAlt").asText()).isEqualTo("Foto de demonstração");
    assertThat(covered.get("imageCount").asInt()).isEqualTo(2);

    // Moving the second photo first makes it the cover, in the list and in the detail.
    assertThat(
            a.put(
                    base + "/images/order",
                    Map.of("version", 2, "imageIds", List.of(ids.get(1), ids.get(0))))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            find(JSON.readTree(a.get(path(tenantA)).body()).get("products"), product)
                .get("coverImageId")
                .asText())
        .isEqualTo(ids.get(1));
    assertThat(JSON.readTree(a.get(base).body()).get("coverImageId").asText())
        .isEqualTo(ids.get(1));

    for (int i = 0; i < 2; i++)
      assertThat(
              a.send(
                      "DELETE",
                      base + "/images/" + ids.get(i) + "?version=" + (3 + i),
                      null,
                      a.csrfToken,
                      null)
                  .statusCode())
          .isEqualTo(200);
    JsonNode cleared = JSON.readTree(a.get(base).body());
    assertThat(cleared.get("coverImageId").isNull()).isTrue();
    assertThat(cleared.get("imageCount").asInt()).isZero();

    // Another owner never sees A's catalog, covers included.
    Browser b = login("catalog-b@example.test");
    assertThat(b.get(path(tenantA)).statusCode()).isEqualTo(404);
  }

  private static JsonNode find(JsonNode list, UUID product) {
    for (JsonNode item : list) if (product.toString().equals(item.get("id").asText())) return item;
    throw new AssertionError("Product not listed: " + product);
  }

  private HttpResponse<String> upload(
      Browser owner,
      UUID tenant,
      UUID product,
      long version,
      byte[] file,
      String filename,
      String type,
      String csrf,
      String origin)
      throws Exception {
    String boundary = "SyntheticBoundary2026";
    var bytes = new java.io.ByteArrayOutputStream();
    bytes.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"version\"\r\n\r\n"
                + version
                + "\r\n--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"alt\"\r\n\r\nFoto de demonstração\r\n--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename
                + "\"\r\nContent-Type: "
                + type
                + "\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    bytes.write(file);
    bytes.write(("\r\n--" + boundary + "--\r\n").getBytes());
    var request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path(tenant) + "/" + product + "/images"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary);
    if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
    if (origin != null) request.header("Origin", origin);
    return owner.client.send(
        request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private UUID create(Browser owner, UUID tenant, String sku) throws Exception {
    var request = product("Public item", sku, "15.90", "3.00");
    request.put("published", true);
    var response = owner.post(path(tenant), request);
    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode item = JSON.readTree(response.body());
    assertThat(item.get("published").asBoolean()).isFalse();
    return UUID.fromString(item.get("id").asText());
  }

  private HttpResponse<String> publish(
      Browser owner, UUID tenant, UUID product, boolean published, long version) throws Exception {
    return owner.put(
        path(tenant) + "/" + product + "/publication",
        Map.of("published", published, "version", version));
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
                .post("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    return browser;
  }

  private static String path(UUID tenant) {
    return "/api/tenants/" + tenant + "/products";
  }

  private static Map<String, Object> product(String name, String sku, Object price, Object cost) {
    Map<String, Object> result = new HashMap<>();
    result.put("name", name);
    result.put("sku", sku);
    result.put("price", price);
    result.put("cost", cost);
    return result;
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

    HttpResponse<String> post(String path, Object body) throws Exception {
      return send("POST", path, JSON.writeValueAsString(body), csrfToken, null);
    }

    HttpResponse<String> put(String path, Object body) throws Exception {
      return send("PUT", path, JSON.writeValueAsString(body), csrfToken, null);
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
