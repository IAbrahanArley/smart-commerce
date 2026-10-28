package com.smartcommerce.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * SC-043 over real HTTP and PostgreSQL: the store OWNER writes company details and institutional
 * pages, page markup is sanitized, and the public storefront shows only what the published store
 * wrote. Another store's owner never reads or writes them.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StoreContentIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic content secret 2026!";
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
    tenantA = store(activeIdentity("categorias-a@example.test"), "conteudo-a");
    tenantB = store(activeIdentity("categorias-b@example.test"), "conteudo-b");
  }

  @Test
  void ownerWritesCompanyDetailsAndOthersGetOne404() throws Exception {
    Browser a = login("categorias-a@example.test");
    String path = "/api/tenants/" + tenantA + "/content/company";
    assertThat(JSON.readTree(a.get(path).body()).get("cnpj").isNull()).isTrue();
    var saved =
        a.send(
            "PUT",
            path,
            Map.of(
                "announcement", "Frete fixo para todo o Brasil",
                "legalName", "Loja Exemplo LTDA",
                "cnpj", "11.222.333/0001-81",
                "phone", "(83) 3333-4444",
                "facebook", "https://facebook.com/loja.exemplo"));
    assertThat(saved.statusCode()).isEqualTo(200);
    JsonNode company = JSON.readTree(saved.body());
    assertThat(company.get("cnpj").asText()).isEqualTo("11222333000181");
    assertThat(company.get("phone").asText()).isEqualTo("558333334444");
    assertThat(company.get("facebook").asText()).isEqualTo("loja.exemplo");
    assertThat(a.send("PUT", path, Map.of("cnpj", "11.222.333/0001-82")).statusCode())
        .isEqualTo(400);
    assertThat(a.raw("PUT", path, "{\"legalName\":\"X\"}", null).statusCode()).isEqualTo(403);

    Browser b = login("categorias-b@example.test");
    assertThat(b.get(path).statusCode()).isEqualTo(404);
    assertThat(b.send("PUT", path, Map.of("legalName", "Invasor")).statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT legal_name FROM store_company WHERE tenant_id=?", String.class, tenantA))
        .isEqualTo("Loja Exemplo LTDA");
  }

  @Test
  void pagesAreSanitizedAndOnlyWrittenOnesArePublic() throws Exception {
    Browser a = login("categorias-a@example.test");
    String pages = "/api/tenants/" + tenantA + "/content/pages";
    JsonNode all = JSON.readTree(a.get(pages).body());
    assertThat(all).hasSize(5);
    assertThat(all.get(0).get("slug").asText()).isEqualTo("sobre");
    assertThat(all.get(0).get("bodyHtml").asText()).isEmpty();

    var saved =
        a.send(
            "PUT",
            pages + "/trocas-e-devolucoes",
            Map.of(
                "bodyHtml",
                "<p onclick=\"x()\">Troca em <strong>7 dias</strong>.</p><script>alert(1)</script>"
                    + "<a href=\"javascript:x\">link</a>"));
    assertThat(saved.statusCode()).isEqualTo(200);
    String body = JSON.readTree(saved.body()).get(1).get("bodyHtml").asText();
    assertThat(body)
        .contains("<strong>7 dias</strong>")
        .doesNotContain("script", "onclick", "href");
    assertThat(a.send("PUT", pages + "/nao-existe", Map.of("bodyHtml", "x")).statusCode())
        .isEqualTo(400);
    // Markup without text removes the page.
    a.send("PUT", pages + "/sobre", Map.of("bodyHtml", "<p>Somos uma loja.</p>"));
    a.send("PUT", pages + "/sobre", Map.of("bodyHtml", "<p> </p>"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM store_page WHERE tenant_id=? AND kind='ABOUT'",
                Integer.class,
                tenantA))
        .isZero();

    Browser b = login("categorias-b@example.test");
    assertThat(b.get(pages).statusCode()).isEqualTo(404);
    assertThat(b.send("PUT", pages + "/sobre", Map.of("bodyHtml", "<p>x</p>")).statusCode())
        .isEqualTo(404);

    // Public: only after publishing, only the written page, only this store's.
    Browser buyer = new Browser();
    assertThat(buyer.get("/api/public/stores/conteudo-a/pages/trocas-e-devolucoes").statusCode())
        .isEqualTo(404);
    jdbc.update("UPDATE store SET status='PUBLISHED' WHERE tenant_id IN (?,?)", tenantA, tenantB);
    JsonNode page =
        JSON.readTree(buyer.get("/api/public/stores/conteudo-a/pages/trocas-e-devolucoes").body());
    assertThat(page.get("title").asText()).isEqualTo("Trocas e devoluções");
    assertThat(page.get("bodyHtml").asText()).contains("<strong>7 dias</strong>");
    assertThat(buyer.get("/api/public/stores/conteudo-a/pages/sobre").statusCode()).isEqualTo(404);
    assertThat(buyer.get("/api/public/stores/conteudo-b/pages/trocas-e-devolucoes").statusCode())
        .isEqualTo(404);
    assertThat(buyer.get("/api/public/stores/conteudo-a/pages/qualquer").statusCode())
        .isEqualTo(404);

    a.send(
        "PUT", "/api/tenants/" + tenantA + "/content/company", Map.of("legalName", "Loja A LTDA"));
    JsonNode catalog = JSON.readTree(buyer.get("/api/public/stores/conteudo-a/catalog").body());
    assertThat(catalog.get("company").get("legalName").asText()).isEqualTo("Loja A LTDA");
    assertThat(catalog.get("pages")).hasSize(1);
    assertThat(catalog.get("pages").get(0).get("slug").asText()).isEqualTo("trocas-e-devolucoes");
    JsonNode other = JSON.readTree(buyer.get("/api/public/stores/conteudo-b/catalog").body());
    assertThat(other.get("company").isNull()).isTrue();
    assertThat(other.get("pages")).isEmpty();
  }

  // --- helpers -----------------------------------------------------------------------------

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
