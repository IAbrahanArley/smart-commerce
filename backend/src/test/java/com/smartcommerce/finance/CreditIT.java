package com.smartcommerce.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.identity.security.IdentityPasswordEncoder;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * SC-055 over HTTP: the store's employee registers a customer and a credit sale in installments,
 * refused above the limit; the customer pays in parts into the open drawer or a bank account,
 * oldest installments first, never beyond what they owe; the sale settles when paid; a sale paid in
 * part cannot be cancelled; the employee sees only the customer being served, with the phone
 * masked; another store's people see and change nothing.
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"orders.maintenance.enabled=false"})
class CreditIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final String PASSWORD = "Synthetic credit integration secret 2026!";
  private static final String HASH = new IdentityPasswordEncoder().encode(PASSWORD);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID tenantB;
  UUID shirt;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user CASCADE");
    UUID ownerA = activeIdentity("dona-crediario@example.test");
    UUID staffA = activeIdentity("vendedor-crediario@example.test");
    UUID ownerB = activeIdentity("dono-outro-crediario@example.test");
    tenantA = store(ownerA, "crediario-a");
    tenantB = store(ownerB, "crediario-b");
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'STAFF', now())",
        tenantA,
        staffA);
    shirt = variant(tenantA, "Camiseta lisa", "CAM-1", "10.00", "4.00", 30);
  }

  @Test
  void customersBuyOnCreditAndPayInPartsOldestInstallmentFirst() throws Exception {
    Browser owner = login("dona-crediario@example.test");
    Browser staff = login("vendedor-crediario@example.test");
    Browser other = login("dono-outro-crediario@example.test");

    // The OWNER registers a customer with e-mail, note and a limit of 100.00.
    String ownerRequest = UUID.randomUUID().toString();
    var created =
        owner.post(
            customers(tenantA, ""),
            customer(ownerRequest, "Maria Souza", "(11) 98765-4321", "Maria@Example.test", "100"));
    assertThat(created.statusCode()).isEqualTo(200);
    JsonNode maria = json(created);
    String mariaId = maria.get("id").asString();
    assertThat(maria.get("phone").asString()).isEqualTo("11987654321");
    assertThat(maria.get("creditLimit").asString()).isEqualTo("100.00");
    // A retry is the same customer; the same request with other data is a conflict.
    assertThat(
            json(owner.post(
                    customers(tenantA, ""),
                    customer(
                        ownerRequest,
                        "Maria Souza",
                        "(11) 98765-4321",
                        "Maria@Example.test",
                        "100")))
                .get("id")
                .asString())
        .isEqualTo(mariaId);
    assertThat(
            owner
                .post(
                    customers(tenantA, ""),
                    customer(ownerRequest, "Maria S.", "11987654321", null, null))
                .statusCode())
        .isEqualTo(409);
    // One customer per e-mail in the store, whatever its letter case.
    var taken =
        owner.post(
            customers(tenantA, ""),
            customer(UUID.randomUUID(), "Outra Maria", "11900001111", "maria@example.test", null));
    assertThat(taken.statusCode()).isEqualTo(409);
    assertThat(json(taken).get("code").asString()).isEqualTo("EMAIL_TAKEN");

    // The employee gives only name and phone, and sees phones masked.
    assertThat(
            staff
                .post(
                    customers(tenantA, ""),
                    customer(UUID.randomUUID(), "João Lima", "11912345678", null, "50"))
                .statusCode())
        .isEqualTo(400);
    JsonNode joao =
        json(
            staff.post(
                customers(tenantA, ""),
                customer(UUID.randomUUID(), "João Lima", "11912345678", null, null)));
    assertThat(joao.get("phone").asString()).isEqualTo("•••• 5678");
    assertThat(joao.get("creditLimit").isNull()).isTrue();
    JsonNode found = json(staff.post(customers(tenantA, "/search"), search("maria")));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).get("phone").asString()).isEqualTo("•••• 4321");
    assertThat(found.get(0).get("creditLimit").isNull()).isTrue();
    assertThat(found.toString()).doesNotContain("example.test");
    assertThat(
            json(owner.post(customers(tenantA, "/search"), search("8765")))
                .get(0)
                .get("phone")
                .asString())
        .isEqualTo("11987654321");
    assertThat(staff.post(customers(tenantA, "/search"), search("m")).statusCode()).isEqualTo(400);
    // The employee matches a phone only whole and never by e-mail, so neither can be guessed.
    assertThat(json(staff.post(customers(tenantA, "/search"), search("4321")))).isEmpty();
    assertThat(json(staff.post(customers(tenantA, "/search"), search("example.test")))).isEmpty();
    assertThat(json(staff.post(customers(tenantA, "/search"), search("(11) 98765-4321"))))
        .hasSize(1);
    // Only the OWNER edits.
    assertThat(
            staff
                .put(
                    customers(tenantA, "/" + mariaId),
                    customer(null, "Maria", "11987654321", null, "1000"))
                .statusCode())
        .isEqualTo(404);

    // 10 shirts on credit (100.00, the whole limit) in 3 installments: the last absorbs the cents.
    String today = today();
    String creditRequest = UUID.randomUUID().toString();
    var sold = staff.post(sales(tenantA), credit(creditRequest, mariaId, 3, today, shirt, 10));
    assertThat(sold.statusCode()).isEqualTo(200);
    JsonNode sale = json(sold);
    String saleId = sale.get("id").asString();
    assertThat(sale.get("paymentMethod").asString()).isEqualTo("STORE_CREDIT");
    assertThat(sale.get("customerName").asString()).isEqualTo("Maria Souza");
    assertThat(sale.get("installmentCount").asInt()).isEqualTo(3);
    assertThat(
            json(staff.post(sales(tenantA), credit(creditRequest, mariaId, 3, today, shirt, 10)))
                .get("id")
                .asString())
        .isEqualTo(saleId);
    // The same request with another first due date is another sale: a conflict.
    String nextMonth = jdbc.queryForObject("SELECT (?::date + 1)::text", String.class, today);
    assertThat(
            staff
                .post(sales(tenantA), credit(creditRequest, mariaId, 3, nextMonth, shirt, 10))
                .statusCode())
        .isEqualTo(409);
    // Across the list, the employee does not see who bought on credit; the OWNER does.
    assertThat(json(staff.get(sales(tenantA))).get("sales").get(0).get("customerName").isNull())
        .isTrue();
    assertThat(json(owner.get(sales(tenantA))).get("sales").get(0).get("customerName").asString())
        .isEqualTo("Maria Souza");
    assertThat(stock(shirt)).isEqualTo(20);
    assertThat(
            jdbc.queryForObject(
                "SELECT string_agg(number || ' ' || amount::text || ' ' || (due_on - ?::date), ','"
                    + " ORDER BY number) FROM credit_installment WHERE counter_sale_id=?::uuid",
                String.class,
                today,
                saleId))
        .matches("1 33.33 0,2 33.33 (28|29|30|31),3 33.34 (59|60|61|62)");
    // The sale is receivable: nothing settled, no money moved.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM finance_entry WHERE counter_sale_id=?::uuid AND settled_at IS NOT NULL",
                Integer.class,
                saleId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM account_movement WHERE counter_sale_id=?::uuid",
                Integer.class,
                saleId))
        .isZero();
    // One more shirt passes the limit, counting what she owes.
    var above = staff.post(sales(tenantA), credit(UUID.randomUUID(), mariaId, 1, today, shirt, 1));
    assertThat(above.statusCode()).isEqualTo(409);
    assertThat(json(above).get("code").asString()).isEqualTo("CREDIT_LIMIT");
    // A first due date in the past, 13 installments or no customer are invalid.
    String yesterday = jdbc.queryForObject("SELECT (?::date - 1)::text", String.class, today);
    assertThat(
            staff
                .post(
                    sales(tenantA),
                    credit(UUID.randomUUID(), joao.get("id").asString(), 1, yesterday, shirt, 1))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            staff
                .post(
                    sales(tenantA),
                    credit(UUID.randomUUID(), joao.get("id").asString(), 13, today, shirt, 1))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            staff
                .post(sales(tenantA), credit(UUID.randomUUID(), null, 1, today, shirt, 1))
                .statusCode())
        .isEqualTo(400);

    // The employee opens Maria: what is open, phone masked, no limit and no history.
    JsonNode seen = json(staff.get(credit(tenantA, "/customers/" + mariaId)));
    assertThat(seen.get("customer").get("phone").asString()).isEqualTo("•••• 4321");
    assertThat(seen.get("open").asString()).isEqualTo("100.00");
    assertThat(seen.get("availableCredit").isNull()).isTrue();
    assertThat(seen.get("installments")).hasSize(3);
    assertThat(seen.get("history")).isEmpty();
    assertThat(seen.toString()).doesNotContain("example.test");
    JsonNode ownerView = json(owner.get(credit(tenantA, "/customers/" + mariaId)));
    assertThat(ownerView.get("availableCredit").asString()).isEqualTo("0.00");
    assertThat(ownerView.get("history")).hasSize(1);
    assertThat(ownerView.get("history").get(0).get("kind").asString()).isEqualTo("SALE");

    // She pays 40.00 into the open drawer: the first installment and 6.67 of the second.
    String drawer =
        json(staff.get("/api/tenants/" + tenantA + "/cash"))
            .get("drawers")
            .get(0)
            .get("id")
            .asString();
    String session =
        json(staff.post(
                "/api/tenants/" + tenantA + "/cash/sessions",
                body("requestId", UUID.randomUUID(), "accountId", drawer, "amount", "0.00")))
            .get("id")
            .asString();
    String receiptRequest = UUID.randomUUID().toString();
    var paid =
        staff.post(receipts(tenantA, mariaId), receipt(receiptRequest, "40.00", session, null));
    assertThat(paid.statusCode()).isEqualTo(200);
    JsonNode afterFirst = json(paid);
    assertThat(afterFirst.get("open").asString()).isEqualTo("60.00");
    assertThat(afterFirst.get("installments")).hasSize(2);
    assertThat(afterFirst.get("installments").get(0).get("received").asString()).isEqualTo("6.67");
    assertThat(afterFirst.get("installments").get(0).get("open").asString()).isEqualTo("26.66");
    assertThat(balance(drawer)).isEqualTo("40.00");
    // A retry is the same receipt; the same request with another amount is a conflict.
    assertThat(
            staff
                .post(receipts(tenantA, mariaId), receipt(receiptRequest, "40.00", session, null))
                .statusCode())
        .isEqualTo(200);
    assertThat(balance(drawer)).isEqualTo("40.00");
    assertThat(
            staff
                .post(receipts(tenantA, mariaId), receipt(receiptRequest, "41.00", session, null))
                .statusCode())
        .isEqualTo(409);
    // Beyond what she owes, into a drawer without its session, or into nothing: refused.
    var over =
        staff.post(receipts(tenantA, mariaId), receipt(UUID.randomUUID(), "60.01", session, null));
    assertThat(over.statusCode()).isEqualTo(409);
    assertThat(json(over).get("code").asString()).isEqualTo("OVERPAYMENT");
    var bare =
        staff.post(receipts(tenantA, mariaId), receipt(UUID.randomUUID(), "1.00", null, drawer));
    assertThat(json(bare).get("code").asString()).isEqualTo("ACCOUNT_NOT_ALLOWED");
    assertThat(
            staff
                .post(receipts(tenantA, mariaId), receipt(UUID.randomUUID(), "1.00", null, null))
                .statusCode())
        .isEqualTo(400);
    // A credit sale paid in part cannot be cancelled, not even by the database.
    var cancel =
        owner.post(
            sales(tenantA) + "/" + saleId + "/cancel",
            JSON.writeValueAsString(Map.of("reason", "Erro de lançamento", "restock", true)));
    assertThat(cancel.statusCode()).isEqualTo(409);
    assertThat(json(cancel).get("code").asString()).isEqualTo("CREDIT_RECEIVED");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE counter_sale SET status='CANCELLED', cancelled_by=created_by,"
                        + " cancelled_at=now(), cancel_reason='Teste', restocked=false WHERE id=?::uuid",
                    saleId))
        .hasMessageContaining("credit sale with receipts");

    // The OWNER receives the rest into a bank account: nothing is open and the sale settles.
    String bank =
        json(owner.post(
                "/api/tenants/" + tenantA + "/finance/accounts",
                body("requestId", UUID.randomUUID(), "type", "BANK", "name", "Banco da loja")))
            .get("id")
            .asString();
    JsonNode settled =
        json(
            owner.post(
                receipts(tenantA, mariaId), receipt(UUID.randomUUID(), "60.00", null, bank)));
    assertThat(settled.get("open").asString()).isEqualTo("0.00");
    assertThat(settled.get("installments")).isEmpty();
    assertThat(settled.get("history")).hasSize(3);
    assertThat(settled.get("history").get(0).get("kind").asString()).isEqualTo("RECEIPT");
    assertThat(settled.get("history").get(0).get("accountName").asString())
        .isEqualTo("Banco da loja");
    assertThat(balance(bank)).isEqualTo("60.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT settled_at IS NOT NULL FROM finance_entry WHERE entry_key=?",
                Boolean.class,
                "counter-sale:" + saleId))
        .isTrue();
    // Receipts, installments and allocations never change.
    assertThatThrownBy(() -> jdbc.update("UPDATE credit_receipt SET amount=amount+1"))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM credit_allocation"))
        .hasMessageContaining("append-only");

    // João buys 20.00 in 2 installments; one becomes overdue; the OWNER's list shows it.
    String joaoId = joao.get("id").asString();
    JsonNode joaoSale =
        json(staff.post(sales(tenantA), credit(UUID.randomUUID(), joaoId, 2, today, shirt, 2)));
    jdbc.execute("ALTER TABLE credit_installment DISABLE TRIGGER credit_installment_append_only");
    jdbc.update(
        "UPDATE credit_installment SET due_on=due_on - 1 WHERE counter_sale_id=?::uuid AND number=1",
        joaoSale.get("id").asString());
    jdbc.execute("ALTER TABLE credit_installment ENABLE TRIGGER credit_installment_append_only");
    JsonNode overview = json(owner.get(credit(tenantA, "?filter=OVERDUE")));
    assertThat(overview.get("open").asString()).isEqualTo("20.00");
    assertThat(overview.get("overdue").asString()).isEqualTo("10.00");
    assertThat(overview.get("overdueCustomers").asLong()).isEqualTo(1);
    assertThat(overview.get("customers")).hasSize(1);
    assertThat(overview.get("customers").get(0).get("name").asString()).isEqualTo("João Lima");
    assertThat(json(owner.get(credit(tenantA, "?filter=ALL"))).get("customers")).hasSize(2);
    assertThat(
            json(owner.get(credit(tenantA, "/customers/" + joaoId)))
                .get("installments")
                .get(0)
                .get("overdue")
                .asBoolean())
        .isTrue();
    assertThat(staff.get(credit(tenantA, "")).statusCode()).isEqualTo(404);
    // Cancelling a credit sale with nothing received voids its installments and restocks.
    assertThat(
            owner
                .post(
                    sales(tenantA) + "/" + joaoSale.get("id").asString() + "/cancel",
                    JSON.writeValueAsString(Map.of("reason", "Desistiu", "restock", true)))
                .statusCode())
        .isEqualTo(200);
    assertThat(json(owner.get(credit(tenantA, "/customers/" + joaoId))).get("open").asString())
        .isEqualTo("0.00");
    assertThat(stock(shirt)).isEqualTo(20);

    // Another store's OWNER sees and changes nothing.
    assertThat(other.get(credit(tenantA, "/customers/" + mariaId)).statusCode()).isEqualTo(404);
    assertThat(
            other
                .post(receipts(tenantA, mariaId), receipt(UUID.randomUUID(), "1.00", session, null))
                .statusCode())
        .isEqualTo(404);
    assertThat(other.post(customers(tenantA, "/search"), search("maria")).statusCode())
        .isEqualTo(404);
    assertThat(json(other.post(customers(tenantB, "/search"), search("maria")))).isEmpty();
    assertThat(other.get(credit(tenantB, "/customers/" + mariaId)).statusCode()).isEqualTo(404);
    UUID foreignShirt = variant(tenantB, "Camiseta B", "CAM-B", "10.00", null, 5);
    assertThat(
            other
                .post(sales(tenantB), credit(UUID.randomUUID(), mariaId, 1, today, foreignShirt, 1))
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void twoReceiptsAtOnceNeverPayBeyondWhatIsOwed() throws Exception {
    Browser owner = login("dona-crediario@example.test");
    String customerId =
        json(owner.post(
                customers(tenantA, ""),
                customer(UUID.randomUUID(), "Ana Paula", "11955554444", null, null)))
            .get("id")
            .asString();
    owner.post(sales(tenantA), credit(UUID.randomUUID(), customerId, 1, today(), shirt, 10));
    String bank =
        json(owner.post(
                "/api/tenants/" + tenantA + "/finance/accounts",
                body("requestId", UUID.randomUUID(), "type", "BANK", "name", "Banco da loja")))
            .get("id")
            .asString();
    var barrier = new CyclicBarrier(2);
    Callable<Integer> pay =
        () -> {
          barrier.await(10, TimeUnit.SECONDS);
          return owner
              .post(receipts(tenantA, customerId), receipt(UUID.randomUUID(), "60.00", null, bank))
              .statusCode();
        };
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(pay);
      var second = pool.submit(pay);
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(balance(bank)).isEqualTo("60.00");
  }

  private static String customer(
      Object request, String name, String phone, String email, String limit) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request == null ? null : request.toString());
    body.put("name", name);
    body.put("phone", phone);
    body.put("email", email);
    body.put("creditLimit", limit);
    return JSON.writeValueAsString(body);
  }

  private static String search(String text) throws Exception {
    return JSON.writeValueAsString(Map.of("q", text));
  }

  private static String credit(
      Object request,
      String customer,
      int installments,
      String firstDue,
      UUID variant,
      int quantity)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request.toString());
    body.put("items", List.of(Map.of("variantId", variant.toString(), "quantity", quantity)));
    body.put("paymentMethod", "STORE_CREDIT");
    body.put("customerId", customer);
    body.put("installments", installments);
    body.put("firstDueOn", firstDue);
    return JSON.writeValueAsString(body);
  }

  private static String receipt(Object request, String amount, String session, String account)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request.toString());
    body.put("amount", amount);
    body.put("cashSessionId", session);
    body.put("accountId", account);
    return JSON.writeValueAsString(body);
  }

  private String today() {
    return jdbc.queryForObject(
        "SELECT (now() AT TIME ZONE 'America/Sao_Paulo')::date::text", String.class);
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

  private static String customers(UUID tenant, String rest) {
    return "/api/tenants/" + tenant + "/customers" + rest;
  }

  private static String credit(UUID tenant, String rest) {
    return "/api/tenants/" + tenant + "/credit" + rest;
  }

  private static String receipts(UUID tenant, String customer) {
    return credit(tenant, "/customers/" + customer + "/receipts");
  }

  private static String sales(UUID tenant) {
    return "/api/tenants/" + tenant + "/counter-sales";
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
