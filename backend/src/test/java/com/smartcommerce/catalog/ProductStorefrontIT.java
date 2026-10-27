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

/**
 * SC-041 over real HTTP and PostgreSQL: brands of a store, the storefront address of a product
 * (generated on creation, editable, former ones kept for redirects) and the "compare at" price of
 * each variant. Another store's owner never reads or uses them.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductStorefrontIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic storefront secret 2026!";
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
    tenantA = store(activeIdentity("categorias-a@example.test"), "vitrine-a");
    tenantB = store(activeIdentity("categorias-b@example.test"), "vitrine-b");
  }

  @Test
  void ownerManagesBrandsAndOthersGetOne404() throws Exception {
    Browser a = login("categorias-a@example.test");
    var created = a.send("POST", brands(tenantA), Map.of("name", " Marca Ágil "));
    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode brand = JSON.readTree(created.body()).get(0);
    assertThat(brand.get("name").asText()).isEqualTo("Marca Ágil");
    assertThat(brand.get("slug").asText()).isEqualTo("marca-agil");
    UUID agil = UUID.fromString(brand.get("id").asText());

    var duplicate = a.send("POST", brands(tenantA), Map.of("name", "MARCA ÁGIL"));
    assertThat(duplicate.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(duplicate.body()).get("code").asText()).isEqualTo("NAME_CONFLICT");
    assertThat(a.send("POST", brands(tenantA), Map.of("name", "")).statusCode()).isEqualTo(400);

    var renamed = a.send("PUT", brands(tenantA) + "/" + agil, Map.of("name", "Ágil Sports"));
    assertThat(JSON.readTree(renamed.body()).get(0).get("slug").asText()).isEqualTo("marca-agil");

    // A product with the brand keeps existing when the brand is removed.
    UUID shirt = product(tenantA, "Camiseta", true);
    assertThat(a.send("PUT", storefront(tenantA, shirt), card(agil, null)).statusCode())
        .isEqualTo(200);
    assertThat(JSON.readTree(a.get(brands(tenantA)).body()).get(0).get("products").asLong())
        .isEqualTo(1);

    Browser b = login("categorias-b@example.test");
    assertThat(JSON.readTree(b.get(brands(tenantB)).body())).isEmpty();
    assertThat(b.get(brands(tenantA)).statusCode()).isEqualTo(404);
    assertThat(b.send("PUT", brands(tenantA) + "/" + agil, Map.of("name", "X")).statusCode())
        .isEqualTo(404);
    assertThat(b.send("DELETE", brands(tenantB) + "/" + agil, null).statusCode()).isEqualTo(404);
    assertThat(a.raw("POST", brands(tenantA), "{\"name\":\"Sem CSRF\"}", null).statusCode())
        .isEqualTo(403);

    assertThat(a.send("DELETE", brands(tenantA) + "/" + agil, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT brand_id FROM catalog_product WHERE id=?", UUID.class, shirt))
        .isNull();
  }

  @Test
  void productsGetAnAddressFromTheirNameAndKeepFormerOnes() throws Exception {
    Browser a = login("categorias-a@example.test");
    JsonNode first = created(a, "Tênis de Corrida", "TEN-1");
    JsonNode second = created(a, "Tênis de corrida", "TEN-2");
    assertThat(first.get("slug").asText()).isEqualTo("tenis-de-corrida");
    assertThat(second.get("slug").asText()).isEqualTo("tenis-de-corrida-2");
    UUID id = UUID.fromString(second.get("id").asText());

    // Taken, malformed, then a new address: the former one is kept for redirects.
    var taken = a.send("PUT", storefront(tenantA, id), card(null, "tenis-de-corrida"));
    assertThat(taken.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(taken.body()).get("code").asText()).isEqualTo("SLUG_CONFLICT");
    assertThat(a.send("PUT", storefront(tenantA, id), card(null, "não vale")).statusCode())
        .isEqualTo(400);
    var changed = a.send("PUT", storefront(tenantA, id), card(null, "tenis-corrida-azul"));
    assertThat(changed.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(changed.body()).get("slug").asText()).isEqualTo("tenis-corrida-azul");
    assertThat(
            jdbc.queryForObject(
                "SELECT product_id FROM catalog_product_slug_history WHERE slug='tenis-de-corrida-2'",
                UUID.class))
        .isEqualTo(id);
    // Taking a former address back removes it from the redirects.
    a.send("PUT", storefront(tenantA, id), card(null, "tenis-de-corrida-2"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM catalog_product_slug_history WHERE slug='tenis-de-corrida-2'",
                Integer.class))
        .isZero();
    // Saving the product form never touches the address.
    var saved =
        a.send(
            "PUT",
            "/api/tenants/" + tenantA + "/products/" + id,
            Map.of(
                "name",
                "Tênis novo",
                "sku",
                "TEN-2",
                "price",
                "199.90",
                "version",
                second.get("version").asLong()));
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(saved.body()).get("slug").asText()).isEqualTo("tenis-de-corrida-2");
  }

  @Test
  void compareAtPricesMustBeAboveThePriceAndStayInTheStore() throws Exception {
    Browser a = login("categorias-a@example.test");
    JsonNode created = created(a, "Jaqueta", "JAQ-1"); // price 199.90
    UUID id = UUID.fromString(created.get("id").asText());
    UUID variant = UUID.fromString(created.get("variants").get(0).get("id").asText());

    var equal = a.send("PUT", storefront(tenantA, id), compareAt(variant, "199.90"));
    assertThat(equal.statusCode()).isEqualTo(400);
    assertThat(JSON.readTree(equal.body()).get("code").asText()).isEqualTo("COMPARE_AT_NOT_ABOVE");
    assertThat(a.send("PUT", storefront(tenantA, id), compareAt(variant, "abc")).statusCode())
        .isEqualTo(400);
    var saved = a.send("PUT", storefront(tenantA, id), compareAt(variant, "249.9"));
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(saved.body()).get("variants").get(0).get("compareAtPrice").asText())
        .isEqualTo("249.90");
    // A variant of another product is a 404; null clears the price.
    UUID other = product(tenantA, "Outro", true);
    assertThat(a.send("PUT", storefront(tenantA, id), compareAt(other, "300.00")).statusCode())
        .isEqualTo(404);
    var cleared = a.send("PUT", storefront(tenantA, id), compareAt(variant, null));
    assertThat(cleared.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(cleared.body()).get("variants").get(0).get("compareAtPrice").isNull())
        .isTrue();

    // Another store: its owner, and its brand, are a 404 here.
    Browser b = login("categorias-b@example.test");
    var foreignBrand = b.send("POST", brands(tenantB), Map.of("name", "Da loja B"));
    UUID foreign = UUID.fromString(JSON.readTree(foreignBrand.body()).get(0).get("id").asText());
    assertThat(a.send("PUT", storefront(tenantA, id), card(foreign, null)).statusCode())
        .isEqualTo(404);
    assertThat(b.send("PUT", storefront(tenantA, id), card(null, "x")).statusCode()).isEqualTo(404);
    assertThat(b.send("PUT", storefront(tenantB, id), card(null, "x")).statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject("SELECT brand_id FROM catalog_product WHERE id=?", UUID.class, id))
        .isNull();
  }

  // --- helpers -----------------------------------------------------------------------------

  private static String brands(UUID tenant) {
    return "/api/tenants/" + tenant + "/brands";
  }

  private static String storefront(UUID tenant, UUID product) {
    return "/api/tenants/" + tenant + "/products/" + product + "/storefront";
  }

  private static Map<String, Object> card(UUID brand, String slug) {
    Map<String, Object> body = new HashMap<>();
    body.put("brandId", brand);
    body.put("slug", slug);
    return body;
  }

  private static Map<String, Object> compareAt(UUID variant, String price) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("id", variant);
    entry.put("compareAtPrice", price);
    Map<String, Object> body = card(null, null);
    body.put("variants", java.util.List.of(entry));
    return body;
  }

  /** Creates a simple product through the API (price 199.90) and returns its view. */
  private JsonNode created(Browser owner, String name, String sku) throws Exception {
    var response =
        owner.send(
            "POST",
            "/api/tenants/" + owner.tenant + "/products",
            Map.of("name", name, "sku", sku, "price", "199.90"));
    assertThat(response.statusCode()).isEqualTo(201);
    return JSON.readTree(response.body());
  }

  private UUID product(UUID tenant, String name, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind) SELECT ?,?,id,?,?,10.00,'BRL',now(),now(),?,'SIMPLE' FROM store WHERE tenant_id=?",
        id,
        tenant,
        name,
        "SKU-" + id.toString().substring(0, 8),
        published,
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
