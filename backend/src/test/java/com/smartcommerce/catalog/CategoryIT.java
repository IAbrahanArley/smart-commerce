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
 * MVP block 1c over real HTTP and PostgreSQL: the OWNER manages flat categories (unique names per
 * store, limit, removal keeps products), assigns them to products, and the storefront lists and
 * filters only published products; another store's owner never reads or uses them.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic categories secret 2026!";
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
    tenantA = store(activeIdentity("categorias-a@example.test"), "categorias-a");
    tenantB = store(activeIdentity("categorias-b@example.test"), "categorias-b");
  }

  @Test
  void ownerManagesCategoriesWithUniqueNamesAndOthersGetOne404() throws Exception {
    Browser a = login("categorias-a@example.test");
    var created = a.send("POST", categories(tenantA), Map.of("name", "  Camisetas  "));
    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode list = JSON.readTree(created.body());
    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("name").asText()).isEqualTo("Camisetas");
    assertThat(list.get(0).get("products").asLong()).isZero();
    UUID camisetas = UUID.fromString(list.get(0).get("id").asText());

    // Same name in another case: a conflict. Invalid names: 400.
    var duplicate = a.send("POST", categories(tenantA), Map.of("name", "CAMISETAS"));
    assertThat(duplicate.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(duplicate.body()).get("code").asText()).isEqualTo("NAME_CONFLICT");
    for (String invalid : new String[] {"", "   ", "x".repeat(61), "Linha\nnova"})
      assertThat(a.send("POST", categories(tenantA), Map.of("name", invalid)).statusCode())
          .as(invalid)
          .isEqualTo(400);

    a.send("POST", categories(tenantA), Map.of("name", "Acessórios"));
    var renamed =
        a.send("PUT", categories(tenantA) + "/" + camisetas, Map.of("name", "Camisetas e regatas"));
    assertThat(renamed.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(renamed.body()).get(1).get("name").asText())
        .isEqualTo("Camisetas e regatas"); // sorted by name

    // B's owner never sees, renames or removes A's categories; A cannot use B's store.
    Browser b = login("categorias-b@example.test");
    assertThat(JSON.readTree(b.get(categories(tenantB)).body())).isEmpty();
    assertThat(b.get(categories(tenantA)).statusCode()).isEqualTo(404);
    assertThat(
            b.send("PUT", categories(tenantA) + "/" + camisetas, Map.of("name", "X")).statusCode())
        .isEqualTo(404);
    assertThat(b.send("DELETE", categories(tenantA) + "/" + camisetas, null).statusCode())
        .isEqualTo(404);
    assertThat(
            b.send("PUT", categories(tenantB) + "/" + camisetas, Map.of("name", "X")).statusCode())
        .isEqualTo(404);
    assertThat(a.get(categories(tenantB)).statusCode()).isEqualTo(404);
    // CSRF protects writes.
    assertThat(a.raw("POST", categories(tenantA), "{\"name\":\"Sem CSRF\"}", null).statusCode())
        .isEqualTo(403);
  }

  @Test
  void productsGetACategoryAndTheStorefrontFiltersOnlyPublishedOnes() throws Exception {
    Browser a = login("categorias-a@example.test");
    UUID camisetas = createCategory(a, "Camisetas");
    UUID canecas = createCategory(a, "Canecas");
    UUID shirt = product(tenantA, "Camiseta azul", true);
    UUID draft = product(tenantA, "Camiseta rascunho", false);
    UUID mug = product(tenantA, "Caneca branca", true);
    UUID loose = product(tenantA, "Chaveiro", true);

    var assigned = a.send("PUT", productCategory(tenantA, shirt), Map.of("categoryId", camisetas));
    assertThat(assigned.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(assigned.body()).get("categoryId").asText())
        .isEqualTo(camisetas.toString());
    a.send("PUT", productCategory(tenantA, draft), Map.of("categoryId", camisetas));
    a.send("PUT", productCategory(tenantA, mug), Map.of("categoryId", canecas));
    assertThat(JSON.readTree(a.get(categories(tenantA)).body()).get(0).get("products").asLong())
        .isEqualTo(2); // Camisetas: the published shirt and the draft

    // Another store's category, or another store's product: one 404, nothing changes.
    Browser b = login("categorias-b@example.test");
    UUID foreign = createCategory(b, "Da loja B");
    assertThat(
            a.send("PUT", productCategory(tenantA, loose), Map.of("categoryId", foreign))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.send("PUT", productCategory(tenantB, shirt), Map.of("categoryId", foreign))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            b.send("PUT", productCategory(tenantA, shirt), Map.of("categoryId", foreign))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT category_id FROM catalog_product WHERE id=?", UUID.class, loose))
        .isNull();

    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id=?", tenantA);
    Browser buyer = new Browser();
    JsonNode all = JSON.readTree(buyer.get("/api/public/stores/categorias-a").body());
    assertThat(all.get("category").isNull()).isTrue();
    assertThat(all.get("categories")).hasSize(2);
    assertThat(all.get("categories").get(0).get("name").asText()).isEqualTo("Camisetas");
    assertThat(all.get("products")).hasSize(3); // the draft is never public

    JsonNode shirts =
        JSON.readTree(buyer.get("/api/public/stores/categorias-a?categoria=" + camisetas).body());
    assertThat(shirts.get("category").asText()).isEqualTo(camisetas.toString());
    assertThat(shirts.get("products")).hasSize(1);
    assertThat(shirts.get("products").get(0).get("name").asText()).isEqualTo("Camiseta azul");
    // Another store's category id simply matches nothing here.
    assertThat(
            JSON.readTree(buyer.get("/api/public/stores/categorias-a?categoria=" + foreign).body())
                .get("products"))
        .isEmpty();
    assertThat(buyer.get("/api/public/stores/categorias-a?categoria=nao-e-uuid").statusCode())
        .isIn(400, 404);

    // Clearing the category, then removing one: products stay, without a category.
    assertThat(
            JSON.readTree(
                    a.send("PUT", productCategory(tenantA, mug), new HashMap<>(Map.of())).body())
                .get("categoryId")
                .isNull())
        .isTrue();
    assertThat(a.send("DELETE", categories(tenantA) + "/" + camisetas, null).statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM catalog_product WHERE tenant_id=? AND category_id IS NOT NULL",
                Integer.class,
                tenantA))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM catalog_product WHERE tenant_id=?", Integer.class, tenantA))
        .isEqualTo(4);
    // Canecas has no published product any more: hidden from the storefront.
    assertThat(JSON.readTree(buyer.get("/api/public/stores/categorias-a").body()).get("categories"))
        .isEmpty();
  }

  @Test
  void aStoreHasAtMostFiftyCategories() throws Exception {
    Browser a = login("categorias-a@example.test");
    for (int i = 0; i < 50; i++) createCategory(a, "Categoria " + i);
    var refused = a.send("POST", categories(tenantA), Map.of("name", "Mais uma"));
    assertThat(refused.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(refused.body()).get("code").asText()).isEqualTo("LIMIT");
  }

  // --- helpers -----------------------------------------------------------------------------

  private static String categories(UUID tenant) {
    return "/api/tenants/" + tenant + "/categories";
  }

  private static String productCategory(UUID tenant, UUID product) {
    return "/api/tenants/" + tenant + "/products/" + product + "/category";
  }

  /** Creates a category in the owner's own store and returns its id. */
  private UUID createCategory(Browser owner, String name) throws Exception {
    var response = owner.send("POST", categories(owner.tenant), Map.of("name", name));
    assertThat(response.statusCode()).isEqualTo(201);
    for (JsonNode category : JSON.readTree(response.body()))
      if (category.get("name").asText().equals(name))
        return UUID.fromString(category.get("id").asText());
    throw new AssertionError("Category not returned: " + name);
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
