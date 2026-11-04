package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * SC-053 over HTTP: the store's employee sells at the counter from the same stock as the
 * storefront; cash goes into the open drawer, Pix into the OWNER's account, cards stay receivable
 * with the informed fee; the employee's discount is capped; only the OWNER cancels and configures;
 * another store's people see and change nothing.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class CounterSaleIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic counter integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID tenantB;
  UUID shirt;
  UUID jeans;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    UUID ownerA = activeIdentity("dona-balcao@example.test");
    UUID staffA = activeIdentity("vendedor-balcao@example.test");
    UUID ownerB = activeIdentity("dono-outro-balcao@example.test");
    tenantA = store(ownerA, "balcao-a");
    tenantB = store(ownerB, "balcao-b");
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'STAFF', now())",
        tenantA,
        staffA);
    shirt = variant(tenantA, "Camiseta lisa", "CAM-1", "10.00", "4.00", 5);
    jeans = variant(tenantA, "Calça jeans", "JEA-1", "25.00", null, 2);
  }

  @Test
  void anEmployeeSellsWithEveryMethodFromTheSharedStockAndTheOwnerCancels() throws Exception {
    Browser owner = login("dona-balcao@example.test");
    Browser staff = login("vendedor-balcao@example.test");
    String bank =
        json(owner.post(
                "/api/tenants/" + tenantA + "/finance/accounts",
                body("requestId", UUID.randomUUID(), "type", "BANK", "name", "Banco da loja")))
            .get("id")
            .asString();
    var settings = new LinkedHashMap<String, Object>();
    settings.put("staffDiscountLimit", "10");
    settings.put("pixAccountId", bank);
    settings.put("debitFeePercent", "2.00");
    settings.put("debitReleaseDays", 1);
    assertThat(
            owner.put(path(tenantA, "/settings"), JSON.writeValueAsString(settings)).statusCode())
        .isEqualTo(200);
    assertThat(
            staff.put(path(tenantA, "/settings"), JSON.writeValueAsString(settings)).statusCode())
        .isEqualTo(404);
    assertThat(staff.get(path(tenantA, "/settings")).statusCode()).isEqualTo(404);
    // Pix goes into a bank account only: a drawer would count money that is not in it.
    var drawerPix = new LinkedHashMap<>(settings);
    drawerPix.put(
        "pixAccountId",
        json(owner.get("/api/tenants/" + tenantA + "/cash"))
            .get("drawers")
            .get(0)
            .get("id")
            .asString());
    assertThat(
            owner.put(path(tenantA, "/settings"), JSON.writeValueAsString(drawerPix)).statusCode())
        .isEqualTo(409);

    // The employee opens the drawer and finds the products by name or SKU, with what is available.
    JsonNode cash = json(staff.get("/api/tenants/" + tenantA + "/cash"));
    String drawer = cash.get("drawers").get(0).get("id").asString();
    String session =
        json(staff.post(
                "/api/tenants/" + tenantA + "/cash/sessions",
                body("requestId", UUID.randomUUID(), "accountId", drawer, "amount", "0.00")))
            .get("id")
            .asString();
    JsonNode found = json(staff.get(path(tenantA, "/products?q=jea")));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).get("available").asInt()).isEqualTo(2);
    assertThat(found.get(0).toString()).doesNotContain("cost");
    assertThat(staff.get(path(tenantA, "/products?q=a")).statusCode()).isEqualTo(400);
    JsonNode overview = json(staff.get(path(tenantA, "")));
    assertThat(overview.get("role").asString()).isEqualTo("STAFF");
    assertThat(overview.get("pixAccountName").asString()).isEqualTo("Banco da loja");
    assertThat(overview.get("sessions").get(0).get("id").asString()).isEqualTo(session);

    // Cash: 2 shirts + 1 jeans = 45.00, 10% off is the employee's limit; 10.01% is not.
    assertThat(
            staff
                .post(
                    path(tenantA, ""),
                    sale(UUID.randomUUID(), "4.51", "CASH", session, shirt, 2, jeans, 1))
                .statusCode())
        .isEqualTo(409);
    String cashRequest = UUID.randomUUID().toString();
    var sold =
        staff.post(
            path(tenantA, ""), sale(cashRequest, "4.50", "CASH", session, shirt, 2, jeans, 1));
    assertThat(sold.statusCode()).isEqualTo(200);
    JsonNode cashSale = json(sold);
    assertThat(cashSale.get("total").asString()).isEqualTo("40.50");
    assertThat(cashSale.toString()).doesNotContain("Cost").doesNotContain("cost");
    assertThat(
            json(staff.post(
                    path(tenantA, ""),
                    sale(cashRequest, "4.50", "CASH", session, shirt, 2, jeans, 1)))
                .get("id")
                .asString())
        .isEqualTo(cashSale.get("id").asString());
    assertThat(balance(drawer)).isEqualTo("40.50");
    assertThat(stock(shirt)).isEqualTo(3);
    assertThat(stock(jeans)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT string_agg(type || ' ' || amount::text || ' ' || (settled_at IS NOT NULL)"
                    + " || ' ' || cost_incomplete, ',' ORDER BY type) FROM finance_entry"
                    + " WHERE counter_sale_id=?::uuid",
                String.class,
                cashSale.get("id").asString()))
        .isEqualTo("COGS -8.00 false true,SALE 40.50 true false"); // a cost moves no money

    // Pix into the OWNER's account; debit receivable with the informed fee; credit fee unknown.
    staff.post(path(tenantA, ""), sale(UUID.randomUUID(), null, "PIX", null, shirt, 1));
    assertThat(balance(bank)).isEqualTo("10.00");
    JsonNode debit =
        json(staff.post(path(tenantA, ""), sale(UUID.randomUUID(), null, "DEBIT", null, shirt, 1)));
    // The fee is margin data: the employee never gets it; the OWNER does.
    assertThat(debit.get("fee").isNull()).isTrue();
    assertThat(json(owner.get(path(tenantA, ""))).get("sales").get(0).get("fee").asString())
        .isEqualTo("0.20");
    assertThat(debit.get("expectedReleaseOn").asString()).isEqualTo(tomorrow());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance_entry WHERE counter_sale_id=?::uuid AND settled_at IS NULL",
                Integer.class,
                debit.get("id").asString()))
        .isEqualTo(3);
    JsonNode credit =
        json(
            staff.post(path(tenantA, ""), sale(UUID.randomUUID(), null, "CREDIT", null, shirt, 1)));
    assertThat(credit.get("fee").isNull()).isTrue();
    JsonNode report = json(owner.get("/api/tenants/" + tenantA + "/finance"));
    assertThat(report.get("grossRevenue").asString()).isEqualTo("70.50");
    assertThat(report.get("informedFees").asString()).isEqualTo("0.20");
    // Cash and Pix moved money (40.50 + 10.00); cards are receivable; costs move no money.
    assertThat(report.get("cashMovement").asString()).isEqualTo("50.50");
    assertThat(report.get("missing").toString())
        .contains("FEES_UNAVAILABLE")
        .contains("\"count\":1");

    // Shirts are gone; the last jeans is reserved by a storefront checkout: both refused, nothing
    // written.
    long before = jdbc.queryForObject("SELECT count(*) FROM counter_sale", Long.class);
    var none = staff.post(path(tenantA, ""), sale(UUID.randomUUID(), null, "PIX", null, shirt, 1));
    assertThat(none.statusCode()).isEqualTo(409);
    assertThat(json(none).get("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
    jdbc.update(
        "INSERT INTO inventory_reservation(id,tenant_id,variant_id,order_id,quantity,status,expires_at,created_at)"
            + " VALUES (?,?,?,?,1,'ACTIVE',now()+interval '20 minutes',now())",
        UUID.randomUUID(),
        tenantA,
        jeans,
        TestOrders.create(jdbc, tenantA));
    assertThat(
            staff
                .post(path(tenantA, ""), sale(UUID.randomUUID(), null, "PIX", null, jeans, 1))
                .statusCode())
        .isEqualTo(409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM counter_sale", Long.class))
        .isEqualTo(before);
    assertThat(stock(jeans)).isEqualTo(1);

    // Only the OWNER cancels; with restock the units and the cash come back.
    String cancel = path(tenantA, "/" + cashSale.get("id").asString() + "/cancel");
    String reason =
        body("reason", "Cliente desistiu", "restock", "true").replace("\"true\"", "true");
    assertThat(staff.post(cancel, reason).statusCode()).isEqualTo(404);
    var cancelResponse = owner.post(cancel, reason);
    assertThat(cancelResponse.statusCode()).as(cancelResponse.body()).isEqualTo(200);
    JsonNode cancelled = json(cancelResponse);
    assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
    assertThat(balance(drawer)).isEqualTo("0.00");
    assertThat(stock(shirt)).isEqualTo(2);
    assertThat(stock(jeans)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount)::text FROM finance_entry WHERE counter_sale_id=?::uuid",
                String.class,
                cashSale.get("id").asString()))
        .isEqualTo("0.00");

    // Another store's OWNER sees nothing and cannot sell A's products.
    Browser other = login("dono-outro-balcao@example.test");
    assertThat(other.get(path(tenantA, "")).statusCode()).isEqualTo(404);
    assertThat(
            other
                .post(path(tenantB, ""), sale(UUID.randomUUID(), null, "DEBIT", null, shirt, 1))
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void twoSalesOfTheLastUnitAtOnceSellItOnce() throws Exception {
    Browser owner = login("dona-balcao@example.test");
    Browser staff = login("vendedor-balcao@example.test");
    jdbc.update("UPDATE inventory_balance SET quantity=1 WHERE product_id=?", jeans);
    CyclicBarrier barrier = new CyclicBarrier(2);
    List<Callable<Integer>> work = new ArrayList<>();
    for (Browser seller : List.of(owner, staff))
      work.add(
          () -> {
            String request = sale(UUID.randomUUID(), null, "DEBIT", null, jeans, 1);
            barrier.await(10, TimeUnit.SECONDS);
            return seller.post(path(tenantA, ""), request).statusCode();
          });
    List<Integer> statuses = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (var result : executor.invokeAll(work)) statuses.add(result.get());
    }
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(stock(jeans)).isZero();
    // Invalid bodies are 400: unknown method, cash without session, duplicated item, zero units.
    for (String invalid :
        List.of(
            sale(UUID.randomUUID(), null, "BOLETO", null, jeans, 1),
            sale(UUID.randomUUID(), null, "CASH", null, jeans, 1),
            sale(UUID.randomUUID(), null, "PIX", null, jeans, 1, jeans, 1),
            sale(UUID.randomUUID(), null, "PIX", null, jeans, 0)))
      assertThat(staff.post(path(tenantA, ""), invalid).statusCode()).as(invalid).isEqualTo(400);
  }

  @Test
  void theSaleListPagesNewestFirstWithoutRepeatingOrSkippingTies() throws Exception {
    Browser staff = login("vendedor-balcao@example.test");
    Browser other = login("dono-outro-balcao@example.test");
    UUID seller =
        jdbc.queryForObject(
            "SELECT id FROM identity_user WHERE email_key='vendedor-balcao@example.test'",
            UUID.class);
    // 52 sales; the 50th, 51st and 52nd share the same instant, right on the page boundary.
    jdbc.update(
        "INSERT INTO counter_sale(id,tenant_id,request_id,payment_method,subtotal_amount,"
            + "discount_amount,total_amount,status,created_by,created_at)"
            + " SELECT gen_random_uuid(),?,gen_random_uuid(),'DEBIT',10,0,10,'ACTIVE',?,"
            + " now() - LEAST(g,50) * interval '1 minute' FROM generate_series(1,52) g",
        tenantA,
        seller);
    JsonNode first = json(staff.get(path(tenantA, "")));
    assertThat(first.get("sales")).hasSize(50);
    String cursor = first.get("nextCursor").asString();
    JsonNode second =
        json(
            staff.get(
                path(
                    tenantA,
                    "?before="
                        + java.net.URLEncoder.encode(
                            cursor, java.nio.charset.StandardCharsets.UTF_8))));
    assertThat(second.get("sales")).hasSize(2);
    assertThat(second.get("nextCursor").isNull()).isTrue();
    var ids = new java.util.HashSet<String>();
    for (JsonNode page : List.of(first, second))
      for (JsonNode sale : page.get("sales")) ids.add(sale.get("id").asString());
    assertThat(ids).hasSize(52);
    // A cursor is only a position: another store reads its own (empty) list, and junk is a 400.
    assertThat(
            json(other.get(
                    path(
                        tenantB,
                        "?before="
                            + java.net.URLEncoder.encode(
                                cursor, java.nio.charset.StandardCharsets.UTF_8))))
                .get("sales"))
        .isEmpty();
    for (String invalid : List.of("x", "2026-01-01T00:00:00Z_nope", "1_" + UUID.randomUUID()))
      assertThat(staff.get(path(tenantA, "?before=" + invalid)).statusCode())
          .as(invalid)
          .isEqualTo(400);
  }

  private static String sale(
      Object request, String discount, String method, String session, Object... lines)
      throws Exception {
    List<Map<String, Object>> items = new ArrayList<>();
    for (int i = 0; i < lines.length; i += 2)
      items.add(Map.of("variantId", lines[i].toString(), "quantity", lines[i + 1]));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request.toString());
    body.put("items", items);
    body.put("discount", discount);
    body.put("paymentMethod", method);
    body.put("cashSessionId", session);
    return JSON.writeValueAsString(body);
  }

  private String tomorrow() {
    return jdbc.queryForObject(
        "SELECT ((now() AT TIME ZONE 'America/Sao_Paulo')::date + 1)::text", String.class);
  }

  private int stock(UUID variant) {
    return jdbc.queryForObject(
        "SELECT quantity FROM inventory_balance WHERE product_id=?", Integer.class, variant);
  }

  private String balance(String account) {
    return jdbc.queryForObject(
        "SELECT COALESCE(sum(amount),0)::numeric(20,2)::text FROM account_movement WHERE account_id=?::uuid",
        String.class,
        account);
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

  private static String path(UUID tenant, String rest) {
    return "/api/tenants/" + tenant + "/counter-sales" + rest;
  }

  private static String body(Object... pairs) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2)
      body.put((String) pairs[i], String.valueOf(pairs[i + 1]));
    return JSON.writeValueAsString(body);
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
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String csrfToken;

    void csrf() throws Exception {
      var response = get("/api/auth/csrf");
      assertThat(response.statusCode()).isEqualTo(200);
      csrfToken = JSON.readTree(response.body()).get("token").asString();
    }

    HttpResponse<String> get(String path) throws Exception {
      return send("GET", path, null, null);
    }

    HttpResponse<String> post(String path, String body) throws Exception {
      return send("POST", path, body, csrfToken);
    }

    HttpResponse<String> put(String path, String body) throws Exception {
      return send("PUT", path, body, csrfToken);
    }

    HttpResponse<String> send(String method, String path, String body, String csrf)
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
