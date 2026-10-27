package com.smartcommerce.storefront;

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
class StorefrontIsolationIT {
  private static final String PROFILE =
      "{\"contactEmail\":null,\"whatsapp\":null,\"instagram\":null,\"logo\":false}";

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
  void publicReadsExposeOnlySelectedFieldsAndRequireBothPublicationFlags() throws Exception {
    Browser owner = login("catalog-a@example.test");
    UUID product = create(owner, tenantA, "FIRST");
    Browser visitor = new Browser();
    String publicPath = "/api/public/stores/catalog-a";
    var missing = visitor.get(publicPath);
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(
            owner
                .put("/api/tenants/" + tenantA + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(JSON.readTree(visitor.get(publicPath).body()).get("products").size()).isZero();
    assertThat(visitor.get(publicPath + "/products/" + product).statusCode()).isEqualTo(404);
    assertThat(publish(owner, tenantA, product, true, 0).statusCode()).isEqualTo(200);
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,7)",
        product,
        tenantA);
    var visible = visitor.get(publicPath);
    assertThat(visible.statusCode()).isEqualTo(200);
    assertThat(visible.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(visible.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode body = JSON.readTree(visible.body());
    assertThat(body)
        .isEqualTo(
            JSON.readTree(
                JSON.writeValueAsString(
                    Map.of(
                        "name",
                        "Synthetic store",
                        "slug",
                        "catalog-a",
                        // MVP block 1b: public contacts and logo, empty until the store sets them.
                        "profile",
                        JSON.readTree(PROFILE),
                        // MVP block 1c: no categories yet, no filter applied.
                        "categories",
                        List.of(),
                        "category",
                        JSON.nullNode(),
                        "products",
                        List.of(
                            Map.of(
                                "id",
                                product.toString(),
                                "name",
                                "Public item",
                                "price",
                                "15.90",
                                "currency",
                                "BRL",
                                "available",
                                true,
                                "descriptionHtml",
                                "",
                                "kind",
                                "SIMPLE",
                                "variants",
                                List.of())),
                        "nextCursor",
                        JSON.nullNode()))));
    JsonNode detail = JSON.readTree(visitor.get(publicPath + "/products/" + product).body());
    assertThat(detail.get("store"))
        .isEqualTo(
            JSON.readTree(
                "{\"name\":\"Synthetic store\",\"slug\":\"catalog-a\",\"profile\":"
                    + PROFILE
                    + "}"));
    // ADR-0010 fields plus the SC-042 storefront details, all public by ADR-0018.
    assertThat(detail.get("product").propertyNames())
        .containsExactlyInAnyOrder(
            "id",
            "name",
            "price",
            "currency",
            "available",
            "descriptionHtml",
            "kind",
            "variants",
            "slug",
            "brand",
            "categoryPath",
            "compareAtPrice",
            "discountPercent");
    assertThat(detail.get("related")).isEmpty();
    for (String field :
        List.of("id", "name", "price", "currency", "available", "descriptionHtml", "kind"))
      assertThat(detail.get("product").get(field))
          .isEqualTo(body.get("products").get(0).get(field));
    assertThat(detail.get("product").get("variants"))
        .isEqualTo(
            JSON.readTree(
                JSON.writeValueAsString(
                    List.of(
                        Map.of(
                            "id",
                            product.toString(),
                            "price",
                            "15.90",
                            "currency",
                            "BRL",
                            "available",
                            true,
                            "options",
                            List.of(),
                            "compareAtPrice",
                            JSON.nullNode())))));
    assertThat(publish(owner, tenantA, product, false, 1).statusCode()).isEqualTo(200);
    assertThat(visitor.get(publicPath + "/products/" + product).statusCode()).isEqualTo(404);
    assertThat(
            owner
                .put("/api/tenants/" + tenantA + "/publication", Map.of("published", false))
                .statusCode())
        .isEqualTo(200);
    assertThat(visitor.get(publicPath).statusCode()).isEqualTo(404);
  }

  @Test
  void publicationRequiresOwnerCsrfStrictBooleanAndFreshProductVersion() throws Exception {
    Browser a = login("catalog-a@example.test");
    Browser b = login("catalog-b@example.test");
    UUID product = create(a, tenantA, "AUTH");
    String store = "/api/tenants/" + tenantA + "/publication";
    String item = path(tenantA) + "/" + product + "/publication";
    assertThat(b.put(store, Map.of("published", true)).statusCode()).isEqualTo(404);
    assertThat(publish(b, tenantA, product, true, 0).statusCode()).isEqualTo(404);
    assertThat(a.put(store, Map.of("published", "true")).statusCode()).isEqualTo(400);
    assertThat(a.put(item, Map.of("published", true)).statusCode()).isEqualTo(400);
    assertThat(a.send("PUT", store, "{\"published\":true}", null, null).statusCode())
        .isEqualTo(403);
    assertThat(
            a.send("PUT", store, "{\"published\":true}", a.csrfToken, "https://foreign.example")
                .statusCode())
        .isEqualTo(403);
    assertThat(publish(a, tenantA, product, true, 0).statusCode()).isEqualTo(200);
    var stale = publish(a, tenantA, product, false, 0);
    assertThat(stale.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(stale.body()).get("code").asText()).isEqualTo("VERSION_CONFLICT");
    assertThat(new Browser().get(path(tenantA)).statusCode()).isEqualTo(401);
    jdbc.update("DELETE FROM tenant_membership WHERE tenant_id=? AND user_id=?", tenantA, ownerA);
    assertThat(a.put(store, Map.of("published", true)).statusCode()).isEqualTo(404);
  }

  @Test
  void publicChainIgnoresRevokedSessionCookiesAndRejectsWritesAndForeignProductIds()
      throws Exception {
    Browser a = login("catalog-a@example.test");
    Browser b = login("catalog-b@example.test");
    UUID one = create(a, tenantA, "A");
    UUID two = create(b, tenantB, "B");
    assertThat(
            a.put("/api/tenants/" + tenantA + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            b.put("/api/tenants/" + tenantB + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(publish(a, tenantA, one, true, 0).statusCode()).isEqualTo(200);
    assertThat(publish(b, tenantB, two, true, 0).statusCode()).isEqualTo(200);
    jdbc.update("UPDATE identity_user SET auth_version=auth_version+1 WHERE id=?", ownerA);
    var publicRead = a.get("/api/public/stores/catalog-a");
    assertThat(publicRead.statusCode()).isEqualTo(200);
    assertThat(publicRead.headers().allValues("Set-Cookie")).isEmpty();
    Browser visitor = new Browser();
    assertThat(visitor.get("/api/public/stores/catalog-a/products/" + two).statusCode())
        .isEqualTo(404);
    assertThat(visitor.get("/api/public/stores/catalog-b/products/" + one).statusCode())
        .isEqualTo(404);
    assertThat(visitor.post("/api/public/stores/catalog-a", Map.of("published", true)).statusCode())
        .isEqualTo(403);
    assertThat(a.get(path(tenantA)).statusCode()).isEqualTo(401);
  }

  @Test
  void publishedListsUseCursorPaginationAndDoNotRevealExactStock() throws Exception {
    Browser a = login("catalog-a@example.test");
    assertThat(
            a.put("/api/tenants/" + tenantA + "/publication", Map.of("published", true))
                .statusCode())
        .isEqualTo(200);
    for (int i = 0; i < 21; i++)
      jdbc.update(
          "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,cost,currency,created_at,updated_at,published) SELECT ?,?,id,'Published item',? ,10.00,1.00,'BRL',now(),now(),true FROM store WHERE tenant_id=?",
          UUID.randomUUID(),
          tenantA,
          "PAGE-" + i,
          tenantA);
    Browser visitor = new Browser();
    String route = "/api/public/stores/catalog-a";
    JsonNode first = JSON.readTree(visitor.get(route).body());
    assertThat(first.get("products").size()).isEqualTo(20);
    var cursor = first.get("nextCursor").asText();
    JsonNode next = JSON.readTree(visitor.get(route + "?after=" + cursor).body());
    assertThat(next.get("products").size()).isEqualTo(1);
    assertThat(next.get("nextCursor").isNull()).isTrue();
    assertThat(next.get("products").get(0).get("id").asText())
        .isNotEqualTo(first.get("products").get(19).get("id").asText());
    assertThat(first.get("products").get(0).get("available").asBoolean()).isFalse();
    assertThat(visitor.get(route + "?after=bad").statusCode()).isEqualTo(400);
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
