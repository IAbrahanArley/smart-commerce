package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
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
 * SC-054 over HTTP: the OWNER previews a spreadsheet of counter sales (nothing written), then
 * confirms it; ready sales take the shared stock and enter the ledger on their own dates, the same
 * file again writes nothing, the confirmation checks the stock again, and nobody else imports.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class CounterImportIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic import integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID shirt;
  UUID jeans;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    UUID ownerA = activeIdentity("dona-planilha@example.test");
    UUID staffA = activeIdentity("vendedor-planilha@example.test");
    activeIdentity("dono-outro-planilha@example.test");
    tenantA = store(ownerA, "planilha-a");
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'STAFF', now())",
        tenantA,
        staffA);
    shirt = variant(tenantA, "Camiseta lisa", "CAM-1", "10.00", "4.00", 5);
    jeans = variant(tenantA, "Calça jeans", "JEA-1", "25.00", "9.00", 2);
  }

  @Test
  void theOwnerPreviewsThenImportsOnceFromTheSharedStock() throws Exception {
    Browser owner = login("dona-planilha@example.test");
    String bank = account(owner, "BANK", "Banco da loja");
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("staffDiscountLimit", "0");
    settings.put("pixAccountId", bank);
    settings.put("debitFeePercent", "2.00");
    settings.put("debitReleaseDays", 1);
    assertThat(
            owner
                .send(
                    "PUT",
                    "/api/tenants/" + tenantA + "/counter-sales/settings",
                    JSON.writeValueAsString(settings),
                    "application/json")
                .statusCode())
        .isEqualTo(200);
    String drawer =
        json(owner.send("GET", "/api/tenants/" + tenantA + "/cash", null, null))
            .get("drawers")
            .get(0)
            .get("id")
            .asString();
    String yesterday = day(-1);
    String today = day(0);
    byte[] file =
        ("Data;Código venda;SKU;Quantidade;Preço unitário;Desconto;Forma de pagamento\r\n"
                + yesterday
                + ";V1;CAM-1;2;10,00;1,00;Dinheiro\r\n"
                + yesterday
                + ";V1;JEA-1;1;25,00;;Dinheiro\r\n"
                + today
                + ";;CAM-1;1;10,00;;Pix\r\n"
                + today
                + ";;CAM-1;1;10,00;;Cartão de débito\r\n"
                + today
                + ";;NAO-EXISTE;1;10,00;;Pix\r\n"
                + today
                + ";;JEA-1;5;25,00;;Pix\r\n"
                + today
                + ";;=CMD();1;10,00;;Pix\r\n")
            .getBytes(Charset.forName("windows-1252"));

    // Preview: three sales ready, two refused (unknown SKU, stock), one bad line; nothing written.
    JsonNode preview = json(owner.upload(path("/preview"), file, null));
    assertThat(preview.get("ready").asLong()).isEqualTo(3);
    assertThat(preview.get("withErrors").asLong()).isEqualTo(2);
    assertThat(preview.get("readyTotal").asString()).isEqualTo("64.00");
    assertThat(preview.get("needsCashAccount").asBoolean()).isTrue();
    assertThat(preview.get("problems").toString()).contains("\"line\":8");
    assertThat(preview.get("sales").toString())
        .contains("NAO-EXISTE")
        .contains("estoque insuficiente");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM counter_sale", Integer.class)).isZero();
    assertThat(stock(shirt)).isEqualTo(5);

    // Only the OWNER imports; cash needs the account it went into.
    Browser staff = login("vendedor-planilha@example.test");
    assertThat(staff.upload(path("/preview"), file, null).statusCode()).isEqualTo(404);
    assertThat(
            login("dono-outro-planilha@example.test")
                .upload(path("/preview"), file, null)
                .statusCode())
        .isEqualTo(404);
    var noAccount = owner.upload(path(""), file, null);
    assertThat(noAccount.statusCode()).isEqualTo(409);
    assertThat(json(noAccount).get("code").asString()).isEqualTo("CASH_ACCOUNT_REQUIRED");
    assertThat(owner.upload(path(""), file, UUID.randomUUID().toString()).statusCode())
        .isEqualTo(409);

    JsonNode result = json(owner.upload(path(""), file, drawer));
    assertThat(result.get("imported").asInt()).isEqualTo(3);
    assertThat(stock(shirt)).isEqualTo(1);
    assertThat(stock(jeans)).isEqualTo(1);
    // The cash sale counts and settles on its own date, and its money lands that day.
    assertThat(
            jdbc.queryForObject(
                "SELECT c.total_amount::text || ' ' || c.sold_on::text || ' '"
                    + " || to_char(e.competence_at AT TIME ZONE 'America/Sao_Paulo','YYYY-MM-DD')"
                    + " || ' ' || (e.settled_at = e.competence_at)"
                    + " FROM counter_sale c JOIN finance_entry e ON e.counter_sale_id=c.id AND e.type='SALE'"
                    + " WHERE c.payment_method='CASH'",
                String.class))
        .isEqualTo("44.00 " + iso(-1) + " " + iso(-1) + " true");
    assertThat(
            jdbc.queryForObject(
                "SELECT string_agg(kind || ' ' || amount::text || ' ' || occurred_on::text, ',')"
                    + " FROM account_movement WHERE account_id=?::uuid",
                String.class,
                drawer))
        .isEqualTo("COUNTER_SALE 44.00 " + iso(-1));
    assertThat(
            jdbc.queryForObject(
                "SELECT COALESCE(sum(amount),0)::text FROM account_movement WHERE account_id=?::uuid",
                String.class,
                bank))
        .isEqualTo("10.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT fee_amount::text || ' ' || expected_release_on::text FROM counter_sale"
                    + " WHERE payment_method='DEBIT'",
                String.class))
        .isEqualTo("0.20 " + iso(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movement WHERE type='COUNTER_SALE'", Integer.class))
        .isEqualTo(4);

    // The same file again (and its preview) writes nothing new.
    JsonNode again = json(owner.upload(path(""), file, drawer));
    assertThat(again.get("imported").asInt()).isZero();
    assertThat(again.get("preview").get("alreadyImported").asLong()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM counter_sale", Integer.class))
        .isEqualTo(3);
    assertThat(stock(shirt)).isEqualTo(1);
  }

  @Test
  void theConfirmationChecksTheStockAgainAndBadFilesAreRefused() throws Exception {
    Browser owner = login("dona-planilha@example.test");
    String bank = account(owner, "BANK", "Banco da loja");
    byte[] file =
        ("data;sku;quantidade;preco_unitario;forma_pagamento\n"
                + day(0)
                + ";CAM-1;2;10,00;dinheiro\n"
                + day(0)
                + ";JEA-1;2;25,00;dinheiro\n")
            .getBytes(StandardCharsets.UTF_8);
    assertThat(json(owner.upload(path("/preview"), file, null)).get("ready").asLong()).isEqualTo(2);
    // An online sale takes a pair of jeans between the preview and the confirmation: the
    // confirmation checks again under the store lock, imports the shirts and refuses the jeans.
    jdbc.update("UPDATE inventory_balance SET quantity=1 WHERE product_id=?", jeans);
    JsonNode result = json(owner.upload(path(""), file, bank));
    assertThat(result.get("imported").asInt()).isEqualTo(1);
    assertThat(result.get("preview").get("withErrors").asLong()).isEqualTo(1);
    assertThat(stock(jeans)).isEqualTo(1);
    assertThat(stock(shirt)).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM counter_sale c WHERE NOT EXISTS"
                    + " (SELECT 1 FROM inventory_movement m WHERE m.counter_sale_id=c.id)",
                Integer.class))
        .isZero();

    JsonNode empty =
        json(
            owner.upload(
                path("/preview"),
                "data;sku;quantidade;preco_unitario;forma_pagamento\n"
                    .getBytes(StandardCharsets.UTF_8),
                null));
    assertThat(empty.get("fileRefused").asBoolean()).isTrue();
    assertThat(
            owner
                .upload(path(""), "data;sku\n".getBytes(StandardCharsets.UTF_8), bank)
                .statusCode())
        .isEqualTo(400);
    assertThat(owner.send("POST", path("/preview"), "{}", "application/json").statusCode())
        .isEqualTo(400);
  }

  private String path(String rest) {
    return "/api/tenants/" + tenantA + "/counter-sales/imports" + rest;
  }

  private String account(Browser owner, String type, String name) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", UUID.randomUUID().toString());
    body.put("type", type);
    body.put("name", name);
    return json(owner.send(
            "POST",
            "/api/tenants/" + tenantA + "/finance/accounts",
            JSON.writeValueAsString(body),
            "application/json"))
        .get("id")
        .asString();
  }

  /**
   * dd/mm/aaaa of today plus {@code days} in São Paulo, as a spreadsheet in Portuguese shows it.
   */
  private String day(int days) {
    return jdbc.queryForObject(
        "SELECT to_char((now() AT TIME ZONE 'America/Sao_Paulo')::date + ?, 'DD/MM/YYYY')",
        String.class,
        days);
  }

  private String iso(int days) {
    return jdbc.queryForObject(
        "SELECT ((now() AT TIME ZONE 'America/Sao_Paulo')::date + ?)::text", String.class, days);
  }

  private int stock(UUID variant) {
    return jdbc.queryForObject(
        "SELECT quantity FROM inventory_balance WHERE product_id=?", Integer.class, variant);
  }

  private UUID variant(UUID tenant, String name, String sku, String price, String cost, int stock) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind)"
            + " SELECT ?,?,id,?,?,?::numeric,'BRL',now(),now(),true,'SIMPLE' FROM store WHERE tenant_id=?",
        id,
        tenant,
        name,
        sku,
        price,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key)"
            + " VALUES (?,?,?,?,?::numeric,?::numeric,'BRL','[]',?)",
        id,
        tenant,
        id,
        sku,
        price,
        cost,
        id.toString());
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,?)",
        id,
        tenant,
        stock);
    return id;
  }

  private static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
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
                    "application/json")
                .statusCode())
        .isEqualTo(204);
    browser.csrf();
    return browser;
  }

  private final class Browser {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    void csrf() throws Exception {
      var response = send("GET", "/api/auth/csrf", null, null);
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asString();
    }

    /** A multipart POST with the file and, optionally, the cash account. */
    HttpResponse<String> upload(String path, byte[] file, String cashAccount) throws Exception {
      String boundary = "SyntheticImportBoundary2026";
      var body = new ByteArrayOutputStream();
      body.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"vendas.csv\"\r\n"
                  + "Content-Type: text/csv\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      body.write(file);
      body.write("\r\n".getBytes(StandardCharsets.UTF_8));
      if (cashAccount != null)
        body.write(
            ("--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\"cashAccountId\"\r\n\r\n"
                    + cashAccount
                    + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
      body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(30))
              .header("X-CSRF-TOKEN", csrfToken)
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> send(String method, String path, String body, String type)
        throws Exception {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(20));
      if (csrfToken != null) request.header("X-CSRF-TOKEN", csrfToken);
      if (type != null) request.header("Content-Type", type);
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(body));
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
