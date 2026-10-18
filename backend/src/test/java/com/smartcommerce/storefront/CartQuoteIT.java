package com.smartcommerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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

/** SC-007: public cart quote contract, strict input and tenant isolation on PostgreSQL. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartQuoteIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID tenantB;
  UUID simpleA;
  UUID variantA;
  UUID emptyA;
  UUID simpleB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    tenantA = store("quote-a", "PUBLISHED");
    tenantB = store("quote-b", "PUBLISHED");
    store("quote-draft", "DRAFT");
    simpleA = simple(tenantA, "PRIVATE-SKU-A", "15.90", 7, true);
    emptyA = simple(tenantA, "PRIVATE-SKU-EMPTY", "9.99", 0, true);
    UUID variable = product(tenantA, "Camiseta", "PRIVATE-SKU-VAR", "20.00", "VARIABLE", true);
    variantA =
        variant(
            tenantA,
            variable,
            "PRIVATE-SKU-AZUL",
            "20.00",
            "[{\"name\":\"Cor\",\"value\":\"Azul\"}]",
            2);
    simpleB = simple(tenantB, "PRIVATE-SKU-B", "5.00", 50, true);
  }

  @Test
  void quotesWithCatalogPricesAndExposesOnlyPublicFields() throws Exception {
    UUID missing = UUID.randomUUID();
    var response =
        quote(
            "quote-a",
            lines(Map.of(simpleA, 3), Map.of(variantA, 5), Map.of(emptyA, 1), Map.of(missing, 1)),
            "SCSESSION=synthetic-not-a-session");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    JsonNode body = JSON.readTree(response.body());
    assertThat(names(body)).containsExactly("currency", "lines", "subtotal", "purchasable");
    assertThat(body.get("currency").asString()).isEqualTo("BRL");
    // Only the AVAILABLE line counts: 3 × 15.90, exact decimal.
    assertThat(body.get("subtotal").asString()).isEqualTo("47.70");
    assertThat(body.get("purchasable").asBoolean()).isFalse();
    JsonNode first = body.get("lines").get(0);
    assertThat(names(first))
        .containsExactly(
            "variantId",
            "quantity",
            "status",
            "productId",
            "productName",
            "options",
            "unitPrice",
            "lineTotal");
    assertThat(first.get("status").asString()).isEqualTo("AVAILABLE");
    assertThat(first.get("productName").asString()).isEqualTo("Produto");
    assertThat(first.get("unitPrice").asString()).isEqualTo("15.90");
    assertThat(first.get("lineTotal").asString()).isEqualTo("47.70");
    assertThat(first.get("quantity").asInt()).isEqualTo(3);
    JsonNode insufficient = body.get("lines").get(1);
    assertThat(insufficient.get("status").asString()).isEqualTo("INSUFFICIENT");
    assertThat(insufficient.get("options"))
        .isEqualTo(JSON.readTree("[{\"name\":\"Cor\",\"value\":\"Azul\"}]"));
    assertThat(insufficient.get("lineTotal").asString()).isEqualTo("100.00");
    assertThat(body.get("lines").get(2).get("status").asString()).isEqualTo("UNAVAILABLE");
    assertThat(body.get("lines").get(3).get("status").asString()).isEqualTo("UNAVAILABLE");
    assertThat(response.body())
        .doesNotContain("PRIVATE-SKU", "sku", "cost", "\"3.00\"", "tenant", "store")
        // Exact balances (7 and 2) never appear as quantities.
        .doesNotContain("\"quantity\":7", "\"quantity\":2");
    var allAvailable = quote("quote-a", lines(Map.of(simpleA, 7)), null);
    JsonNode ok = JSON.readTree(allAvailable.body());
    assertThat(ok.get("purchasable").asBoolean()).isTrue();
    assertThat(ok.get("subtotal").asString()).isEqualTo("111.30");
  }

  @Test
  void foreignUnpublishedAndMissingVariantsAreIndistinguishableAndDraftsAre404() throws Exception {
    UUID hidden = simple(tenantA, "PRIVATE-SKU-HIDDEN", "1.00", 3, false);
    UUID missing = UUID.randomUUID();
    JsonNode lines =
        JSON.readTree(
                quote(
                        "quote-a",
                        lines(Map.of(simpleB, 1), Map.of(hidden, 1), Map.of(missing, 1)),
                        null)
                    .body())
            .get("lines");
    for (int i = 0; i < 3; i++) {
      JsonNode line = lines.get(i);
      assertThat(line.get("status").asString()).isEqualTo("UNAVAILABLE");
      for (String field : List.of("productId", "productName", "options", "unitPrice", "lineTotal"))
        assertThat(line.get(field).isNull()).isTrue();
    }
    assertThat(lines.get(0).toString().replace(simpleB.toString(), "X"))
        .isEqualTo(lines.get(2).toString().replace(missing.toString(), "X"))
        .isEqualTo(lines.get(1).toString().replace(hidden.toString(), "X"));
    // Same variant is real and sellable in its own store.
    assertThat(
            JSON.readTree(quote("quote-b", lines(Map.of(simpleB, 1)), null).body())
                .get("lines")
                .get(0)
                .get("status")
                .asString())
        .isEqualTo("AVAILABLE");
    for (String slug : List.of("quote-draft", "quote-missing", "QUOTE-A", "a"))
      assertThat(quote(slug, lines(Map.of(simpleA, 1)), null).statusCode()).isEqualTo(404);
    // Unpublishing the product makes the line unavailable on the next quote.
    jdbc.update("UPDATE catalog_product SET published=false WHERE id=?", simpleA);
    assertThat(
            JSON.readTree(quote("quote-a", lines(Map.of(simpleA, 1)), null).body())
                .get("lines")
                .get(0)
                .get("status")
                .asString())
        .isEqualTo("UNAVAILABLE");
  }

  @Test
  void tamperedBodiesAreRejectedWithoutServerErrorsOrWrites() throws Exception {
    String id = simpleA.toString();
    long balances = jdbc.queryForObject("SELECT sum(quantity) FROM inventory_balance", Long.class);
    long movements = jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Long.class);
    List<String> invalid = new ArrayList<>();
    invalid.add("{");
    invalid.add("[]");
    invalid.add("null");
    invalid.add("{}");
    invalid.add("{\"items\":[]}");
    invalid.add("{\"items\":{}}");
    invalid.add("{\"items\":[{\"variantId\":\"" + id + "\",\"quantity\":1}]} {}");
    invalid.add("{\"items\":[],\"items\":[]}");
    invalid.add("{\"items\":[{\"variantId\":\"" + id + "\",\"quantity\":1}],\"extra\":1}");
    for (String quantity : List.of("0", "11", "-1", "1.5", "\"1\"", "null", "99999999999"))
      invalid.add("{\"items\":[{\"variantId\":\"" + id + "\",\"quantity\":" + quantity + "}]}");
    for (String variant : List.of("\"not-a-uuid\"", "1", "null", "\"" + id + " \""))
      invalid.add("{\"items\":[{\"variantId\":" + variant + ",\"quantity\":1}]}");
    invalid.add("{\"items\":[{\"variantId\":\"" + id + "\",\"quantity\":1,\"price\":\"0.01\"}]}");
    invalid.add(
        "{\"items\":[{\"variantId\":\""
            + id
            + "\",\"quantity\":1},{\"variantId\":\""
            + id
            + "\",\"quantity\":2}]}");
    List<Map<UUID, Integer>> tooMany = new ArrayList<>();
    for (int i = 0; i < 21; i++) tooMany.add(Map.of(UUID.randomUUID(), 1));
    invalid.add(lines(tooMany.toArray(Map[]::new)));
    for (String body : invalid) {
      var response = send("quote-a", body, "application/json", null);
      assertThat(response.statusCode()).as(body).isEqualTo(400);
      assertThat(response.body()).doesNotContain("Exception", "jackson", "SQL");
    }
    var oversized =
        send(
            "quote-a",
            "{\"items\":[{\"variantId\":\"" + id + "\",\"quantity\":1}]}" + " ".repeat(17000),
            "application/json",
            null);
    assertThat(oversized.statusCode()).isEqualTo(413);
    // 415 is dispatched to /error, which no chain permits, so the visitor sees a generic 403.
    assertThat(send("quote-a", lines(Map.of(simpleA, 1)), "text/plain", null).statusCode())
        .isIn(403, 415);
    var get =
        client.send(
            HttpRequest.newBuilder(uri("quote-a")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(get.statusCode()).isEqualTo(403);
    assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM inventory_balance", Long.class))
        .isEqualTo(balances);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Long.class))
        .isEqualTo(movements);
  }

  @Test
  void quoteUsesAvailableStockNetOfActiveReservationsOnly() throws Exception {
    // simpleA has 7 on hand: an active reservation of 5 leaves 2 sellable.
    reserve(tenantA, simpleA, 5, "now() + interval '30 minutes'");
    // Logically expired reservations stop counting without the expiry routine.
    reserve(tenantA, simpleA, 2, "now() - interval '1 minute'");
    // Another tenant's reservation never changes tenant A's availability.
    reserve(tenantB, simpleB, 50, "now() + interval '30 minutes'");
    JsonNode three = JSON.readTree(quote("quote-a", lines(Map.of(simpleA, 3)), null).body());
    assertThat(three.get("lines").get(0).get("status").asString()).isEqualTo("INSUFFICIENT");
    assertThat(three.get("purchasable").asBoolean()).isFalse();
    JsonNode two = JSON.readTree(quote("quote-a", lines(Map.of(simpleA, 2)), null).body());
    assertThat(two.get("lines").get(0).get("status").asString()).isEqualTo("AVAILABLE");
    assertThat(two.get("subtotal").asString()).isEqualTo("31.80");
    // Fully reserved in tenant B: nothing sellable there, while tenant A was unaffected above.
    JsonNode foreign = JSON.readTree(quote("quote-b", lines(Map.of(simpleB, 1)), null).body());
    assertThat(foreign.get("lines").get(0).get("status").asString()).isEqualTo("UNAVAILABLE");
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM inventory_balance WHERE product_id=?",
                Integer.class,
                simpleA))
        .isEqualTo(7);
  }

  private void reserve(UUID tenant, UUID variant, int quantity, String expiresAt) {
    jdbc.update(
        "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at)"
            + " VALUES (?,?,?,?,?,'ACTIVE',"
            + expiresAt
            + ",now() - interval '2 hours')",
        UUID.randomUUID(),
        tenant,
        variant,
        UUID.randomUUID(),
        quantity);
  }

  @SafeVarargs
  private static String lines(Map<UUID, Integer>... lines) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (var line : lines)
      line.forEach(
          (id, quantity) -> items.add(Map.of("variantId", id.toString(), "quantity", quantity)));
    // Map.of ordering is irrelevant for the strict parser; it only checks the field set.
    return JSON.writeValueAsString(Map.of("items", items));
  }

  private static List<String> names(JsonNode node) {
    return new ArrayList<>(node.propertyNames());
  }

  private URI uri(String slug) {
    return URI.create("http://127.0.0.1:" + port + "/api/public/stores/" + slug + "/cart/quote");
  }

  private HttpResponse<String> quote(String slug, String body, String cookie) throws Exception {
    return send(slug, body, "application/json", cookie);
  }

  private HttpResponse<String> send(String slug, String body, String type, String cookie)
      throws Exception {
    var request =
        HttpRequest.newBuilder(uri(slug))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", type)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) request.header("Cookie", cookie);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private UUID store(String slug, String status) {
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, status, created_at) VALUES (?, ?, 'Synthetic store', ?, ?, now())",
        UUID.randomUUID(),
        tenant,
        slug,
        status);
    return tenant;
  }

  private UUID simple(UUID tenant, String sku, String price, int stock, boolean published) {
    UUID product = product(tenant, "Produto", sku, price, "SIMPLE", published);
    return variant(tenant, product, product, sku, price, "[]", stock);
  }

  private UUID product(
      UUID tenant, String name, String sku, String price, String kind, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,cost,currency,created_at,updated_at,published,kind) SELECT ?,?,id,?,?,?::numeric,3.00,'BRL',now(),now(),?,? FROM store WHERE tenant_id=?",
        id,
        tenant,
        name,
        sku,
        price,
        published,
        kind,
        tenant);
    return id;
  }

  private UUID variant(
      UUID tenant, UUID product, String sku, String price, String options, int stock) {
    return variant(tenant, product, UUID.randomUUID(), sku, price, options, stock);
  }

  private UUID variant(
      UUID tenant, UUID product, UUID id, String sku, String price, String options, int stock) {
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,?::numeric,3.00,'BRL',?,?)",
        id,
        tenant,
        product,
        sku,
        price,
        options,
        id.toString());
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,?)",
        id,
        tenant,
        stock);
    return id;
  }
}
