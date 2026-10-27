package com.smartcommerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * SC-042 over real HTTP and PostgreSQL: the public catalog searches without accents, filters by
 * category subtree, brand, price and variant option, counts facets, sorts and pages, and never
 * mixes another store's products, categories, brands or sales into any number.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicCatalogIT {
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
  UUID oculos;
  UUID camiseta;
  UUID regata;
  UUID bone;

  @BeforeEach
  void catalogOfTwoStores() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    tenantA = store("catalogo-a", true);
    tenantB = store("catalogo-b", true);
    UUID feminino = category(tenantA, null, "Feminino", "feminino", 0, true);
    UUID camisetas = category(tenantA, feminino, "Camisetas", "camisetas", 0, true);
    UUID acessorios = category(tenantA, null, "Acessórios", "acessorios", 1, true);
    UUID oculto = category(tenantA, null, "Oculta", "oculta", 2, false);
    UUID agil = brand(tenantA, "Ágil", "agil");
    UUID solar = brand(tenantA, "Solar", "solar");

    oculos = product(tenantA, "Óculos de Sol Esportivo", "oculos", acessorios, solar, 2);
    variant(oculos, "429.00", "499.00", "[]");
    camiseta = product(tenantA, "Camiseta Técnica", "camiseta", camisetas, agil, 1);
    variant(camiseta, "149.90", null, "[{\"name\":\"Tamanho\",\"value\":\"M\"}]");
    variant(camiseta, "159.90", null, "[{\"name\":\"Tamanho\",\"value\":\"G\"}]");
    regata = product(tenantA, "Regata Dry", "regata", feminino, agil, 3);
    variant(regata, "79.90", "99.90", "[{\"name\":\"Tamanho\",\"value\":\"P\"}]");
    bone = product(tenantA, "Boné", "bone", oculto, null, 4);
    variant(bone, "59.90", null, "[]");
    UUID rascunho = product(tenantA, "Óculos rascunho", "rascunho", acessorios, solar, 5);
    variant(rascunho, "10.00", null, "[]");
    jdbc.update("UPDATE catalog_product SET published=false WHERE id=?", rascunho);

    // Store B: same names and slugs, and the best seller of B must not count in A.
    UUID categoriaB = category(tenantB, null, "Acessórios", "acessorios", 0, true);
    UUID marcaB = brand(tenantB, "Solar", "solar");
    UUID oculosB = product(tenantB, "Óculos da loja B", "oculos", categoriaB, marcaB, 0);
    variant(oculosB, "1.00", null, "[]");
    sale(tenantB, oculosB, 9);
    sale(tenantA, regata, 3);
    sale(tenantA, camiseta, 1);
  }

  @Test
  void searchesWithoutAccentsAndCountsFacetsOfOneStore() throws Exception {
    JsonNode found = catalog("catalogo-a", "q=oculos");
    assertThat(names(found)).containsExactly("Óculos de Sol Esportivo"); // no draft, no store B
    assertThat(found.get("total").asLong()).isEqualTo(1);
    assertThat(found.get("sort").asText()).isEqualTo("relevancia");
    JsonNode card = found.get("products").get(0);
    assertThat(card.get("slug").asText()).isEqualTo("oculos");
    assertThat(card.get("brand").asText()).isEqualTo("Solar");
    assertThat(card.get("price").asText()).isEqualTo("429.00");
    assertThat(card.get("compareAtPrice").asText()).isEqualTo("499.00");
    assertThat(card.get("discountPercent").asInt()).isEqualTo(14); // 14.02% rounded down
    assertThat(card.has("sku")).isFalse();
    assertThat(card.has("cost")).isFalse();

    // A brand name finds its products; prefixes work; an injected tsquery is just text.
    assertThat(names(catalog("catalogo-a", "q=agil")))
        .containsExactlyInAnyOrder("Camiseta Técnica", "Regata Dry");
    assertThat(names(catalog("catalogo-a", "q=camis"))).containsExactly("Camiseta Técnica");
    assertThat(catalog("catalogo-a", "q=" + encode("oculos') | ('x")).get("total").asLong())
        .isZero();

    JsonNode all = catalog("catalogo-a", "");
    assertThat(all.get("total").asLong()).isEqualTo(4); // the hidden category hides no product
    assertThat(menuSlugs(all.get("menu"))).containsExactly("feminino", "camisetas", "acessorios");
    assertThat(counts(all.get("facets").get("brands"))).containsExactly("agil=2", "solar=1");
    assertThat(counts(all.get("facets").get("categories")))
        .containsExactly("feminino=2", "acessorios=1");
    JsonNode sizes = all.get("facets").get("options").get(0);
    assertThat(sizes.get("name").asText()).isEqualTo("Tamanho");
    assertThat(sizes.get("values")).hasSize(3);
    assertThat(all.get("facets").get("prices").get(0).get("min").asText()).isEqualTo("50.00");
  }

  @Test
  void filtersByCategorySubtreeBrandPriceAndOption() throws Exception {
    JsonNode feminino = catalog("catalogo-a", "categoria=feminino");
    assertThat(names(feminino)).containsExactlyInAnyOrder("Camiseta Técnica", "Regata Dry");
    assertThat(feminino.get("category").get("path")).hasSize(1);
    assertThat(counts(feminino.get("facets").get("categories"))).containsExactly("camisetas=1");
    JsonNode camisetas = catalog("catalogo-a", "categoria=camisetas");
    assertThat(camisetas.get("category").get("path").get(0).get("slug").asText())
        .isEqualTo("feminino");

    // A hidden category is a 404 like a foreign or missing one.
    assertThat(get("/api/public/stores/catalogo-a/catalog?categoria=oculta").statusCode())
        .isEqualTo(404);
    assertThat(get("/api/public/stores/catalogo-a/catalog?categoria=nao-existe").statusCode())
        .isEqualTo(404);

    // Brand filter: the brand facet still offers the other brands.
    JsonNode solar = catalog("catalogo-a", "marca=solar");
    assertThat(names(solar)).containsExactly("Óculos de Sol Esportivo");
    assertThat(counts(solar.get("facets").get("brands"))).containsExactly("agil=2", "solar=1");
    assertThat(names(catalog("catalogo-a", "marca=agil,solar"))).hasSize(3);

    assertThat(names(catalog("catalogo-a", "preco_min=60&preco_max=150")))
        .containsExactlyInAnyOrder("Camiseta Técnica", "Regata Dry");
    assertThat(names(catalog("catalogo-a", "opcao=" + encode("Tamanho:G"))))
        .containsExactly("Camiseta Técnica");
    assertThat(
            names(
                catalog(
                    "catalogo-a",
                    "opcao=" + encode("Tamanho:G") + "&opcao=" + encode("Tamanho:P"))))
        .containsExactlyInAnyOrder("Camiseta Técnica", "Regata Dry");

    for (String invalid :
        new String[] {
          "preco_min=abc",
          "preco_min=10&preco_max=5",
          "ordem=nome",
          "pagina=0",
          "pagina=abc",
          "opcao=semvalor",
          "marca=" + encode("Ágil"),
          "q=" + "a".repeat(101)
        })
      assertThat(get("/api/public/stores/catalogo-a/catalog?" + invalid).statusCode())
          .as(invalid)
          .isEqualTo(400);
  }

  @Test
  void sortsAndPagesWithoutRepeatingProducts() throws Exception {
    assertThat(names(catalog("catalogo-a", "ordem=menor-preco")))
        .containsExactly("Boné", "Regata Dry", "Camiseta Técnica", "Óculos de Sol Esportivo");
    assertThat(names(catalog("catalogo-a", "ordem=maior-preco")).getFirst())
        .isEqualTo("Óculos de Sol Esportivo");
    // Regata: 20% off; óculos: 14%; the rest have no "compare at" price.
    assertThat(names(catalog("catalogo-a", "ordem=maior-desconto")).subList(0, 2))
        .containsExactly("Regata Dry", "Óculos de Sol Esportivo");
    // Store B's sale does not count; recent first among products without sales.
    assertThat(names(catalog("catalogo-a", "ordem=mais-vendidos")).subList(0, 2))
        .containsExactly("Regata Dry", "Camiseta Técnica");
    assertThat(names(catalog("catalogo-a", "ordem=recentes")))
        .containsExactly("Camiseta Técnica", "Óculos de Sol Esportivo", "Regata Dry", "Boné");

    for (int i = 0; i < 25; i++)
      variant(product(tenantA, "Meia " + i, "meia-" + i, null, null, 10 + i), "9.90", null, "[]");
    JsonNode first = catalog("catalogo-a", "ordem=menor-preco");
    JsonNode second = catalog("catalogo-a", "ordem=menor-preco&pagina=2");
    assertThat(first.get("products")).hasSize(24);
    assertThat(second.get("products")).hasSize(5);
    assertThat(first.get("total").asLong()).isEqualTo(29);
    List<String> both = new ArrayList<>(names(first));
    both.addAll(names(second));
    assertThat(both).doesNotHaveDuplicates().hasSize(29);
  }

  @Test
  void productPageShowsBrandPathCompareAtAndRelated() throws Exception {
    JsonNode page = json(get("/api/public/stores/catalogo-a/products/" + camiseta));
    JsonNode product = page.get("product");
    assertThat(product.get("slug").asText()).isEqualTo("camiseta");
    assertThat(product.get("brand").get("slug").asText()).isEqualTo("agil");
    assertThat(product.get("categoryPath").get(0).get("slug").asText()).isEqualTo("feminino");
    assertThat(product.get("categoryPath").get(1).get("slug").asText()).isEqualTo("camisetas");
    assertThat(product.get("compareAtPrice").isNull()).isTrue();
    assertThat(page.get("related")).isEmpty(); // nothing else in Camisetas

    JsonNode regataPage = json(get("/api/public/stores/catalogo-a/products/" + regata));
    assertThat(regataPage.get("product").get("compareAtPrice").asText()).isEqualTo("99.90");
    assertThat(regataPage.get("product").get("discountPercent").asInt()).isEqualTo(20);
    assertThat(regataPage.get("product").get("variants").get(0).get("compareAtPrice").asText())
        .isEqualTo("99.90");

    // The boné sits in a hidden category: no path, but the product is still public.
    JsonNode bonePage = json(get("/api/public/stores/catalogo-a/products/" + bone));
    assertThat(bonePage.get("product").get("categoryPath")).isEmpty();

    // Related: same category, available, not itself, never another store's.
    UUID outro = product(tenantA, "Óculos de Grau", "oculos-grau", categoryOf(oculos), null, 0);
    variant(outro, "300.00", null, "[]");
    JsonNode oculosPage = json(get("/api/public/stores/catalogo-a/products/" + oculos));
    assertThat(oculosPage.get("related")).hasSize(1);
    assertThat(oculosPage.get("related").get(0).get("slug").asText()).isEqualTo("oculos-grau");

    // A "compare at" price at or below the price is never shown.
    jdbc.update("UPDATE catalog_variant SET compare_at_price=price WHERE product_id=?", regata);
    assertThat(
            json(get("/api/public/stores/catalogo-a/products/" + regata))
                .get("product")
                .get("compareAtPrice")
                .isNull())
        .isTrue();
  }

  @Test
  void findsAProductByItsAddressAndRedirectsOldOnes() throws Exception {
    JsonNode found = json(get("/api/public/stores/catalogo-a/p/oculos"));
    assertThat(found.get("movedTo").isNull()).isTrue();
    assertThat(found.get("product").get("product").get("name").asText())
        .isEqualTo("Óculos de Sol Esportivo");
    // Same address in store B is store B's product.
    assertThat(
            json(get("/api/public/stores/catalogo-b/p/oculos"))
                .get("product")
                .get("product")
                .get("name")
                .asText())
        .isEqualTo("Óculos da loja B");

    jdbc.update("UPDATE catalog_product SET slug='oculos-sol' WHERE id=?", oculos);
    jdbc.update(
        """
        INSERT INTO catalog_product_slug_history(store_id,slug,tenant_id,product_id,replaced_at)
        SELECT store_id,'oculos',tenant_id,id,now() FROM catalog_product WHERE id=?
        """,
        oculos);
    assertThat(json(get("/api/public/stores/catalogo-a/p/oculos")).get("movedTo").asText())
        .isEqualTo("oculos-sol");
    // After 90 days the old address is forgotten; a draft or unknown address is a 404.
    jdbc.update("UPDATE catalog_product_slug_history SET replaced_at=now()-interval '91 days'");
    assertThat(get("/api/public/stores/catalogo-a/p/oculos").statusCode()).isEqualTo(404);
    assertThat(get("/api/public/stores/catalogo-a/p/rascunho").statusCode()).isEqualTo(404);
    assertThat(get("/api/public/stores/catalogo-a/p/Nao_Vale").statusCode()).isEqualTo(404);
  }

  @Test
  void installmentsArePromisedOnlyWhileMercadoPagoIsConnected() throws Exception {
    jdbc.update("UPDATE store SET max_installments=6 WHERE tenant_id=?", tenantA);
    assertThat(catalog("catalogo-a", "").get("maxInstallments").isNull()).isTrue();
    jdbc.update(
        """
        INSERT INTO payment_account(tenant_id,provider,provider_user_id,status,access_token,
          refresh_token,access_expires_at,key_version,connected_at)
        VALUES (?,'MERCADO_PAGO','123','CONNECTED',decode('00','hex'),decode('00','hex'),
          now()+interval '150 days',1,now())
        """,
        tenantA);
    assertThat(catalog("catalogo-a", "").get("maxInstallments").asInt()).isEqualTo(6);
    assertThat(
            json(get("/api/public/stores/catalogo-a/products/" + oculos))
                .get("maxInstallments")
                .asInt())
        .isEqualTo(6);
    // Store B has no connection and no limit: nothing promised there.
    assertThat(catalog("catalogo-b", "").get("maxInstallments").isNull()).isTrue();
  }

  @Test
  void anUnpublishedStoreHasNoCatalog() throws Exception {
    jdbc.update("UPDATE store SET status='DRAFT' WHERE tenant_id=?", tenantA);
    assertThat(get("/api/public/stores/catalogo-a/catalog").statusCode()).isEqualTo(404);
    assertThat(get("/api/public/stores/catalogo-b/catalog").statusCode()).isEqualTo(200);
  }

  // --- helpers -----------------------------------------------------------------------------

  private JsonNode catalog(String store, String query) throws Exception {
    var response = get("/api/public/stores/" + store + "/catalog?" + query);
    assertThat(response.statusCode()).as(query).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    return JSON.readTree(response.body());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static JsonNode json(HttpResponse<String> response) throws Exception {
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private UUID categoryOf(UUID product) {
    return jdbc.queryForObject(
        "SELECT category_id FROM catalog_product WHERE id=?", UUID.class, product);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static List<String> names(JsonNode page) {
    List<String> names = new ArrayList<>();
    for (JsonNode card : page.get("products")) names.add(card.get("name").asText());
    return names;
  }

  private static List<String> counts(JsonNode facet) {
    List<String> counts = new ArrayList<>();
    for (JsonNode entry : facet)
      counts.add(entry.get("slug").asText() + "=" + entry.get("count").asLong());
    return counts;
  }

  private static List<String> menuSlugs(JsonNode menu) {
    List<String> slugs = new ArrayList<>();
    for (JsonNode item : menu) {
      slugs.add(item.get("slug").asText());
      slugs.addAll(menuSlugs(item.get("children")));
    }
    return slugs;
  }

  private UUID store(String slug, boolean published) {
    UUID tenant = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, status, created_at) VALUES (?, ?, 'Loja', ?, ?, now())",
        UUID.randomUUID(),
        tenant,
        slug,
        published ? "PUBLISHED" : "DRAFT");
    return tenant;
  }

  private UUID category(
      UUID tenant, UUID parent, String name, String slug, int position, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_category(id,tenant_id,store_id,parent_id,name,name_key,slug,position,published,created_at)
        SELECT ?,?,id,?,?,lower(?),?,?,?,now() FROM store WHERE tenant_id=?
        """,
        id,
        tenant,
        parent,
        name,
        name,
        slug,
        position,
        published,
        tenant);
    return id;
  }

  private UUID brand(UUID tenant, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_brand(id,tenant_id,store_id,name,name_key,slug,created_at)
        SELECT ?,?,id,?,lower(?),?,now() FROM store WHERE tenant_id=?
        """,
        id,
        tenant,
        name,
        name,
        slug,
        tenant);
    return id;
  }

  /** {@code age}: smaller is more recent. */
  private UUID product(UUID tenant, String name, String slug, UUID category, UUID brand, int age) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,
          published,kind,slug,category_id,brand_id)
        SELECT ?,?,id,?,?,1.00,'BRL',now()-make_interval(hours=>?),now(),true,'SIMPLE',?,?,? FROM store WHERE tenant_id=?
        """,
        id,
        tenant,
        name,
        "SKU-" + id.toString().substring(0, 8),
        age,
        slug,
        category,
        brand,
        tenant);
    return id;
  }

  private void variant(UUID product, String price, String compareAt, String options) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key,compare_at_price)
        SELECT ?,tenant_id,id,?,?::numeric,NULL,'BRL',?,?,?::numeric FROM catalog_product WHERE id=?
        """,
        id,
        "V-" + id.toString().substring(0, 8),
        price,
        options,
        id.toString(),
        compareAt,
        product);
    // Stock, so the card is available.
    jdbc.update(
        """
        INSERT INTO inventory_balance(product_id,tenant_id,quantity)
        SELECT id,tenant_id,5 FROM catalog_variant WHERE id=?
        """,
        id);
  }

  /** A paid order of {@code quantity} units of the product's first variant. */
  private void sale(UUID tenant, UUID product, int quantity) {
    UUID order = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO sales_order(id,tenant_id,store_id,number,status,currency,subtotal_amount,
          shipping_amount,discount_amount,total_amount,fulfillment_type,fulfillment_text,
          buyer_name,buyer_email,buyer_email_key,buyer_phone,reservation_expires_at,request_id,
          request_fingerprint,access_token_hash,created_at,updated_at,paid_at,refunded_at,
          paid_without_stock)
        SELECT ?,s.tenant_id,s.id,?,'PAID','BRL',10.00,0,0,10.00,'PICKUP','Retirada',
          'Comprador','b@example.test','b@example.test','11999990000',now()+interval '30 minutes',
          ?,repeat('0',64),repeat('0',64),now()-interval '2 days',now(),now()-interval '1 day',NULL,false
        FROM store s WHERE s.tenant_id=?
        """,
        order,
        ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
        UUID.randomUUID(),
        tenant);
    jdbc.update(
        """
        INSERT INTO sales_order_item(id,tenant_id,order_id,variant_id,product_id,position,
          product_name_snapshot,variant_options_snapshot,sku_snapshot,unit_price_snapshot,
          unit_cost_snapshot,quantity,line_total)
        SELECT ?,?,?,v.id,v.product_id,0,'Item','[]','SKU',10.00,NULL,?,10.00*?
        FROM catalog_variant v WHERE v.product_id=? ORDER BY v.id LIMIT 1
        """,
        UUID.randomUUID(),
        tenant,
        order,
        quantity,
        quantity,
        product);
  }
}
