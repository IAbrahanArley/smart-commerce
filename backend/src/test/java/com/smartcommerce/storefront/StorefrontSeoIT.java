package com.smartcommerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.catalog.services.CategoryService;
import com.smartcommerce.tenant.services.TenantService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * SC-047 on PostgreSQL: the sitemap lists only what the published store shows, product addresses
 * tell current, moved and unknown apart for the storefront proxy, and committed changes (only
 * those) send a signed revalidation of the store to storefront-web.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StorefrontSeoIT {
  static final String SECRET = "synthetic-revalidation-secret-for-tests-0001";

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  /** Synthetic storefront-web: records every revalidation call it receives. */
  static final LinkedBlockingQueue<Map<String, String>> CALLS = new LinkedBlockingQueue<>();

  static final HttpServer STOREFRONT = storefront();

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired TenantService tenants;
  @Autowired CategoryService categories;
  UUID owner;
  UUID tenantA;
  UUID tenantB;

  @DynamicPropertySource
  static void revalidation(DynamicPropertyRegistry registry) {
    registry.add(
        "storefront.revalidation.url",
        () -> "http://127.0.0.1:" + STOREFRONT.getAddress().getPort());
    registry.add("storefront.revalidation.secret", () -> SECRET);
  }

  @AfterAll
  static void stopStorefront() {
    STOREFRONT.stop(0);
  }

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    owner = identity("seo-a@example.test");
    tenantA = store(owner, "seo-a", "PUBLISHED");
    tenantB = store(identity("seo-b@example.test"), "seo-b", "DRAFT");
    CALLS.clear();
  }

  @Test
  void sitemapListsOnlyWhatThePublishedStoreShows() throws Exception {
    UUID shirts = category(tenantA, null, "Camisetas", "camisetas", true);
    UUID hidden = category(tenantA, null, "Oculta", "oculta", false);
    category(tenantA, null, "Vazia", "vazia", true);
    product(tenantA, "Camiseta azul", "camiseta-azul", shirts, true);
    product(tenantA, "Rascunho", "rascunho", shirts, false);
    product(tenantA, "Escondida", "escondida", hidden, true);
    jdbc.update(
        "INSERT INTO store_page(store_id,tenant_id,kind,body_html,updated_at)"
            + " SELECT id,tenant_id,'RETURNS','<p>Troca</p>',now() FROM store WHERE tenant_id=?",
        tenantA);

    var response = get("/api/public/stores/seo-a/sitemap");
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode sitemap = JSON.readTree(response.body());
    assertThat(sitemap.get("products").findValuesAsString("slug"))
        .containsExactlyInAnyOrder("camiseta-azul", "escondida");
    assertThat(Instant.parse(sitemap.get("products").get(0).get("updatedAt").asString()))
        .isBefore(Instant.now().plusSeconds(5));
    assertThat(sitemap.get("categories")).hasSize(1);
    assertThat(sitemap.get("categories").get(0).asString()).isEqualTo("camisetas");
    assertThat(sitemap.get("pages").get(0).asString()).isEqualTo("trocas-e-devolucoes");
    // A draft store has no sitemap.
    assertThat(get("/api/public/stores/seo-b/sitemap").statusCode()).isEqualTo(404);
  }

  @Test
  void productAddressesAreCurrentMovedOrUnknown() throws Exception {
    UUID product = product(tenantA, "Camiseta", "camiseta-nova", null, true);
    jdbc.update(
        "INSERT INTO catalog_product_slug_history(store_id,slug,tenant_id,product_id,replaced_at)"
            + " SELECT id,'camiseta-velha',tenant_id,?,now() FROM store WHERE tenant_id=?",
        product,
        tenantA);
    JsonNode current =
        JSON.readTree(get("/api/public/stores/seo-a/p/camiseta-nova/address").body());
    assertThat(current.get("movedTo").isNull()).isTrue();
    assertThat(current.get("product").isNull()).isTrue();
    JsonNode moved = JSON.readTree(get("/api/public/stores/seo-a/p/camiseta-velha/address").body());
    assertThat(moved.get("movedTo").asString()).isEqualTo("camiseta-nova");
    assertThat(get("/api/public/stores/seo-a/p/nao-existe/address").statusCode()).isEqualTo(404);
    assertThat(get("/api/public/stores/seo-b/p/camiseta-nova/address").statusCode()).isEqualTo(404);
    assertThat(get("/api/public/stores/seo-a/p/Inv%C3%A1lido/address").statusCode()).isEqualTo(404);
  }

  @Test
  void committedChangesSendASignedRevalidationOfTheirStoreOnly() throws Exception {
    UUID shirts = category(tenantA, null, "Camisetas", "camisetas", true);
    categories.publish(owner, tenantA, shirts, false);
    Map<String, String> call = CALLS.poll(5, TimeUnit.SECONDS);
    assertThat(call).isNotNull();
    assertThat(call.get("body")).isEqualTo("{\"store\":\"seo-a\"}");
    assertThat(Math.abs(Long.parseLong(call.get("timestamp")) - Instant.now().getEpochSecond()))
        .isLessThan(60);
    assertThat(call.get("signature")).isEqualTo(hmac("seo-a\n" + call.get("timestamp")));

    // A store going back to draft also drops its pages.
    tenants.publish(owner, tenantA, false);
    assertThat(CALLS.poll(5, TimeUnit.SECONDS)).containsEntry("body", "{\"store\":\"seo-a\"}");

    // Rolled back (foreign tenant, missing category): nothing is sent.
    assertThatThrownBy(() -> categories.publish(owner, tenantB, shirts, true))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> categories.publish(owner, tenantA, UUID.randomUUID(), true))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> tenants.publish(owner, tenantB, true))
        .isInstanceOf(RuntimeException.class);
    assertThat(CALLS.poll(1, TimeUnit.SECONDS)).isNull();
  }

  // --- helpers -----------------------------------------------------------------------------

  private static HttpServer storefront() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/interno/revalidar",
          exchange -> {
            CALLS.add(
                Map.of(
                    "body",
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    "timestamp",
                    exchange.getRequestHeaders().getFirst("X-SC-Revalidate-Timestamp"),
                    "signature",
                    exchange.getRequestHeaders().getFirst("X-SC-Revalidate-Signature")));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
          });
      server.start();
      return server;
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static String hmac(String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private UUID identity(String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, ?, ?, 'ACTIVE', now(), now(), now())",
        id,
        email,
        email);
    return id;
  }

  private UUID store(UUID owner, String slug, String status) {
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'OWNER', now())",
        tenant,
        owner);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, status, created_at) VALUES (?, ?, 'Synthetic store', ?, ?, now())",
        UUID.randomUUID(),
        tenant,
        slug,
        status);
    return tenant;
  }

  private UUID category(UUID tenant, UUID parent, String name, String slug, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_category(id,tenant_id,store_id,parent_id,name,name_key,slug,position,published,created_at)
        SELECT ?,?,id,?,?,lower(?),?,0,?,now() FROM store WHERE tenant_id=?
        """,
        id,
        tenant,
        parent,
        name,
        name,
        slug,
        published,
        tenant);
    return id;
  }

  private UUID product(UUID tenant, String name, String slug, UUID category, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,
          published,kind,slug,category_id)
        SELECT ?,?,id,?,?,1.00,'BRL',now(),now(),?,'SIMPLE',?,? FROM store WHERE tenant_id=?
        """,
        id,
        tenant,
        name,
        "SKU-" + id.toString().substring(0, 8),
        published,
        slug,
        category,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,1.00,NULL,'BRL','[]',?)",
        id,
        tenant,
        id,
        "SKU-" + id.toString().substring(0, 8),
        id.toString());
    return id;
  }
}
