package com.smartcommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.security.TokenCipher;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * SC-013 against a loopback fake of Mercado Pago's {@code POST /checkout/preferences} (ADR-0013 P1
 * change): the preference is created from the backend snapshot with the seller's own token and an
 * idempotency key derived from the order, a retry never shows a second checkout, a provider failure
 * keeps the order pending, a store without a connected account takes no orders, and A/B isolation
 * holds.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orders.checkout.enabled=true",
      "deployment.environment=hosted-test",
      "orders.client-ip.secret=synthetic-forwarding-secret-for-tests-only-0013",
      "orders.maintenance.enabled=false",
      "payments.token-refresh.enabled=false",
      "payments.events.enabled=false",
      "payments.reconciliation.enabled=false"
    })
class CheckoutPaymentIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String RETURN_ORIGIN = "https://loja.example.test";
  private static final String NOTIFICATION_URL =
      "https://api.example.test/api/public/webhooks/mercado-pago";
  private static final String TOKEN_A = "APP_USR-synthetic-seller-a";
  private static final String TOKEN_B = "APP_USR-synthetic-seller-b";
  private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
  private static final Map<String, String> IDS_BY_KEY = new ConcurrentHashMap<>();
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final AtomicReference<Function<JsonNode, Reply>> NEXT = new AtomicReference<>();
  private static final HttpServer PROVIDER = provider();

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PaymentProviders providers;
  UUID tenantA;
  UUID tenantB;
  UUID variantA;
  UUID variantB;

  record Call(String authorization, String idempotencyKey, JsonNode body) {}

  record Reply(int status, String body) {}

  private static HttpServer provider() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/checkout/preferences",
          exchange -> {
            JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
            CALLS.add(
                new Call(
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Idempotency-Key"),
                    body));
            Reply reply = NEXT.get().apply(body);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.setExecutor(Executors.newFixedThreadPool(4));
      server.start();
      return server;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void mercadoPago(DynamicPropertyRegistry registry) {
    registry.add("payments.mercado-pago.client-id", () -> "1234567890");
    registry.add("payments.mercado-pago.client-secret", () -> "synthetic-client-secret");
    registry.add(
        "payments.mercado-pago.redirect-uri",
        () -> "http://127.0.0.1:3000/api/payments/mercado-pago/callback");
    registry.add(
        "payments.mercado-pago.api-url",
        () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
    registry.add("payments.mercado-pago.return-origin", () -> RETURN_ORIGIN);
    registry.add("payments.mercado-pago.notification-url", () -> NOTIFICATION_URL);
    registry.add(
        "payments.token-encryption-key",
        () -> Base64.getEncoder().encodeToString("synthetic-32-byte-test-key-00013".getBytes()));
  }

  @AfterAll
  static void stopProvider() {
    PROVIDER.stop(0);
  }

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, order_rate_limit CASCADE");
    CALLS.clear();
    IDS_BY_KEY.clear();
    SEQUENCE.set(0);
    NEXT.set(CheckoutPaymentIT::created);
    tenantA = store("pagar-a");
    tenantB = store("pagar-b");
    variantA = variant(tenantA, "Camiseta", "15.90", "[{\"name\":\"Cor\",\"value\":\"Azul\"}]");
    variantB = variant(tenantB, "Caneca", "5.00", "[]");
    connect(tenantA, "111111", TOKEN_A);
    connect(tenantB, "222222", TOKEN_B);
  }

  @Test
  void createsThePreferenceFromTheSnapshotWithTheSellersTokenAndNoBuyerData(CapturedOutput output)
      throws Exception {
    Order order = checkout("pagar-a", variantA, 2, true, "44.30"); // 2 × 15.90 + 12.50
    assertThat(order.view().get("payable").asBoolean()).isTrue();

    var paid = pay("pagar-a", order.id(), order.token());
    assertThat(paid.statusCode()).isEqualTo(200);
    assertThat(paid.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    String url = JSON.readTree(paid.body()).get("checkoutUrl").asString();
    assertThat(url).startsWith("https://www.mercadopago.com.br/checkout/");

    assertThat(CALLS).hasSize(1);
    Call call = CALLS.getFirst();
    assertThat(call.authorization()).isEqualTo("Bearer " + TOKEN_A);
    assertThat(call.idempotencyKey())
        .isEqualTo(
            UUID.nameUUIDFromBytes(
                    ("sc-checkout-pro:" + order.id()).getBytes(StandardCharsets.UTF_8))
                .toString());
    JsonNode body = call.body();
    assertThat(body.get("external_reference").asString()).isEqualTo(order.id());
    assertThat(body.get("expires").asBoolean()).isTrue();
    var from = OffsetDateTime.parse(body.get("expiration_date_from").asString());
    var to = OffsetDateTime.parse(body.get("expiration_date_to").asString());
    assertThat(Duration.between(from, to)).isEqualTo(Duration.ofMinutes(30));
    assertThat(body.get("items")).hasSize(2);
    assertThat(body.get("items").get(0).get("title").asString()).isEqualTo("Camiseta (Cor: Azul)");
    assertThat(body.get("items").get(0).get("unit_price").isNumber()).isTrue();
    assertThat(body.get("items").get(0).get("unit_price").decimalValue())
        .isEqualByComparingTo("15.90");
    assertThat(body.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    assertThat(body.get("items").get(0).get("currency_id").asString()).isEqualTo("BRL");
    assertThat(body.get("items").get(1).get("title").asString()).isEqualTo("Frete");
    assertThat(body.get("items").get(1).get("unit_price").decimalValue())
        .isEqualByComparingTo("12.50");
    assertThat(body.get("payment_methods").get("excluded_payment_types"))
        .extracting(type -> type.get("id").asString())
        .contains("ticket", "atm", "digital_currency")
        .doesNotContain("credit_card", "debit_card", "bank_transfer", "account_money");
    String back = RETURN_ORIGIN + "/lojas/pagar-a/pedidos/" + order.id() + "/pagamento";
    assertThat(body.get("back_urls").get("success").asString()).isEqualTo(back);
    assertThat(body.get("back_urls").get("failure").asString()).isEqualTo(back);
    assertThat(body.get("back_urls").get("pending").asString()).isEqualTo(back);
    assertThat(body.get("auto_return").asString()).isEqualTo("approved");
    assertThat(body.get("notification_url").asString()).isEqualTo(NOTIFICATION_URL);
    // P11: no payer data leaves the platform; the provider collects it.
    assertThat(body.has("payer")).isFalse();
    assertThat(body.toString()).doesNotContain("Sintético", "example.test\"", "98888", "01001");

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT status,amount,provider_user_id,provider_order_id,checkout_url,provider_status,"
                + "provider_expires_at,tenant_id FROM payment WHERE order_id=?",
            UUID.fromString(order.id()));
    assertThat(row.get("status")).isEqualTo("CREATED");
    assertThat(row.get("amount").toString()).isEqualTo("44.30");
    assertThat(row.get("provider_user_id")).isEqualTo("111111");
    assertThat(row.get("provider_order_id")).isEqualTo("111111-pref-1");
    assertThat(row.get("checkout_url")).isEqualTo(url);
    assertThat(row.get("provider_status")).isEqualTo("created");
    assertThat(row.get("tenant_id")).isEqualTo(tenantA);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sales_order WHERE id=?",
                String.class,
                UUID.fromString(order.id())))
        .isEqualTo("PENDING_PAYMENT");
    // Preference ids start with the collector id, so it appears only inside the provider's URL.
    assertThat(paid.body().replace(url, "")).doesNotContain(TOKEN_A, "111111");
    assertThat(output.getAll()).doesNotContain(TOKEN_A, TOKEN_B);
  }

  @Test
  void retriesAndConcurrentAttemptsShowOneCheckout() throws Exception {
    Order order = checkout("pagar-a", variantA, 1, false, "15.90");
    List<Callable<HttpResponse<String>>> attempts = new ArrayList<>();
    CyclicBarrier barrier = new CyclicBarrier(4);
    for (int i = 0; i < 4; i++)
      attempts.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return pay("pagar-a", order.id(), order.token());
          });
    List<String> urls = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(4)) {
      for (var result : executor.invokeAll(attempts)) {
        assertThat(result.get().statusCode()).isEqualTo(200);
        urls.add(JSON.readTree(result.get().body()).get("checkoutUrl").asString());
      }
    }
    assertThat(urls).hasSize(4).containsOnly(urls.getFirst());
    // Calls that raced past the claim all used the same key; the first stored answer is shown.
    assertThat(CALLS).isNotEmpty().extracting(Call::idempotencyKey).containsOnly(key(order));
    assertThat(IDS_BY_KEY).hasSize(1);

    int calls = CALLS.size();
    var again = pay("pagar-a", order.id(), order.token());
    assertThat(JSON.readTree(again.body()).get("checkoutUrl").asString())
        .isEqualTo(urls.getFirst());
    assertThat(CALLS).hasSize(calls); // a created payment is answered from the database
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payment WHERE order_id=?",
                Integer.class,
                UUID.fromString(order.id())))
        .isEqualTo(1);
  }

  @Test
  void providerFailureKeepsTheOrderPendingAndTheBuyerCanRetry() throws Exception {
    Order order = checkout("pagar-a", variantA, 1, false, "15.90");
    NEXT.set(body -> new Reply(500, "{\"message\":\"internal_error\"}"));
    var failed = pay("pagar-a", order.id(), order.token());
    assertThat(failed.statusCode()).isEqualTo(502);
    assertThat(JSON.readTree(failed.body()).get("code").asString()).isEqualTo("PAYMENT_FAILED");
    assertThat(paymentStatus(order)).isEqualTo("CREATING");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sales_order WHERE id=?",
                String.class,
                UUID.fromString(order.id())))
        .isEqualTo("PENDING_PAYMENT");

    NEXT.set(CheckoutPaymentIT::created);
    var retried = pay("pagar-a", order.id(), order.token());
    assertThat(retried.statusCode()).isEqualTo(200);
    assertThat(paymentStatus(order)).isEqualTo("CREATED");
    assertThat(CALLS).extracting(Call::idempotencyKey).containsOnly(key(order));
  }

  @Test
  void unexpectedProviderAnswersNeverRedirectTheBuyer() throws Exception {
    Order order = checkout("pagar-a", variantA, 1, false, "15.90");
    for (Function<JsonNode, Reply> reply :
        List.<Function<JsonNode, Reply>>of(
            body -> answer("PREF-X", "https://evil.example/checkout", reference(body)),
            body -> answer("PREF-X", "http://www.mercadopago.com.br/checkout", reference(body)),
            body -> answer("PREF-X", "https://mercadopago.com.br.evil.example/x", reference(body)),
            body ->
                answer("PREF-X", "https://www.mercadopago.com.br/c", UUID.randomUUID().toString()),
            body -> answer("PREF X", "https://www.mercadopago.com.br/c", reference(body)),
            body -> new Reply(201, "not json"))) {
      NEXT.set(reply);
      var response = pay("pagar-a", order.id(), order.token());
      assertThat(response.statusCode()).isEqualTo(502);
      assertThat(response.body()).doesNotContain("evil", "PREF");
    }
    assertThat(paymentStatus(order)).isEqualTo("CREATING");
  }

  @Test
  void storeWithoutConnectedAccountTakesNoOrdersAndCannotBePaid() throws Exception {
    Order order = checkout("pagar-a", variantA, 1, false, "15.90");
    jdbc.update(
        "UPDATE payment_account SET status='DISCONNECTED', access_token=NULL, refresh_token=NULL,"
            + " access_expires_at=NULL, key_version=NULL, closed_at=now() WHERE tenant_id=?",
        tenantA);

    var refused = postOrder("pagar-a", variantA, 1, false, "15.90");
    assertThat(refused.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(refused.body()).get("code").asString())
        .isEqualTo("PAYMENT_UNAVAILABLE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_order WHERE tenant_id=?", Integer.class, tenantA))
        .isEqualTo(1);

    var status = get("pagar-a", order.id(), order.token());
    assertThat(JSON.readTree(status.body()).get("payable").asBoolean()).isFalse();
    var pay = pay("pagar-a", order.id(), order.token());
    assertThat(pay.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(pay.body()).get("code").asString()).isEqualTo("PAYMENT_UNAVAILABLE");
    assertThat(CALLS).isEmpty();
  }

  @Test
  void onlyPendingOrdersInsideTheReservationCanBePaid() throws Exception {
    Order expired = checkout("pagar-a", variantA, 1, false, "15.90");
    jdbc.update(
        "UPDATE sales_order SET created_at=now()-interval '2 hours',"
            + " reservation_expires_at=now()-interval '1 minute' WHERE id=?",
        UUID.fromString(expired.id()));
    Order cancelled = checkout("pagar-a", variantA, 1, false, "15.90");
    jdbc.update(
        "UPDATE sales_order SET status='CANCELLED', cancelled_at=now(),"
            + " cancelled_by=(SELECT user_id FROM tenant_membership WHERE tenant_id=?),"
            + " cancel_reason='Teste sintético' WHERE id=?",
        tenantA,
        UUID.fromString(cancelled.id()));
    for (Order order : List.of(expired, cancelled)) {
      var response = pay("pagar-a", order.id(), order.token());
      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("NOT_PAYABLE");
      assertThat(
              JSON.readTree(get("pagar-a", order.id(), order.token()).body())
                  .get("payable")
                  .asBoolean())
          .isFalse();
    }
    assertThat(CALLS).isEmpty();
  }

  @Test
  void eachOrderIsPaidWithItsOwnStoresTokenAndForeignAttemptsAreOne404() throws Exception {
    Order a = checkout("pagar-a", variantA, 1, false, "15.90");
    Order b = checkout("pagar-b", variantB, 1, false, "5.00");
    assertThat(pay("pagar-b", b.id(), b.token()).statusCode()).isEqualTo(200);
    assertThat(CALLS.getLast().authorization()).isEqualTo("Bearer " + TOKEN_B);
    assertThat(pay("pagar-a", a.id(), a.token()).statusCode()).isEqualTo(200);
    assertThat(CALLS.getLast().authorization()).isEqualTo("Bearer " + TOKEN_A);
    assertThat(CALLS).hasSize(2);

    String wrong = a.token().substring(0, 42) + (a.token().endsWith("A") ? "B" : "A");
    for (var attempt :
        List.of(
            pay("pagar-b", a.id(), a.token()), // A's order through B's store
            pay("pagar-a", b.id(), a.token()), // B's order with A's token
            pay("pagar-a", a.id(), wrong),
            pay("pagar-a", a.id(), null),
            pay("pagar-a", UUID.randomUUID().toString(), a.token()))) {
      assertThat(attempt.statusCode()).isEqualTo(404);
      assertThat(attempt.body()).doesNotContain("mercadopago");
    }
    assertThat(CALLS).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT provider_user_id FROM payment WHERE order_id=?",
                String.class,
                UUID.fromString(b.id())))
        .isEqualTo("222222");
  }

  private static Reply created(JsonNode body) {
    String key = CALLS.getLast().idempotencyKey();
    String id =
        IDS_BY_KEY.computeIfAbsent(key, ignored -> "111111-pref-" + SEQUENCE.incrementAndGet());
    Map<String, Object> reply = new LinkedHashMap<>();
    reply.put("id", id);
    reply.put("collector_id", 111111);
    reply.put("init_point", "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=" + id);
    reply.put(
        "sandbox_init_point",
        "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=" + id);
    reply.put("external_reference", reference(body));
    reply.put("items", body.get("items"));
    return new Reply(201, JSON.writeValueAsString(reply));
  }

  private static String reference(JsonNode body) {
    return body.get("external_reference").asString();
  }

  private static Reply answer(String id, String url, String reference) {
    return new Reply(
        201,
        JSON.writeValueAsString(
            Map.of("id", id, "init_point", url, "external_reference", reference)));
  }

  private static String key(Order order) {
    return UUID.nameUUIDFromBytes(
            ("sc-checkout-pro:" + order.id()).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private String paymentStatus(Order order) {
    return jdbc.queryForObject(
        "SELECT status FROM payment WHERE order_id=?", String.class, UUID.fromString(order.id()));
  }

  record Order(String id, String token, JsonNode view) {}

  private Order checkout(String slug, UUID variant, int quantity, boolean shipping, String total)
      throws Exception {
    var response = postOrder(slug, variant, quantity, shipping, total);
    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode result = JSON.readTree(response.body());
    return new Order(
        result.get("order").get("id").asString(),
        result.get("accessToken").asString(),
        result.get("order"));
  }

  private HttpResponse<String> postOrder(
      String slug, UUID variant, int quantity, boolean shipping, String total) throws Exception {
    Map<String, Object> fulfillment = new LinkedHashMap<>();
    if (shipping) {
      fulfillment.put("type", "FIXED_SHIPPING");
      fulfillment.put(
          "address",
          Map.of(
              "line1",
              "Rua Sintética, 100",
              "district",
              "Centro",
              "city",
              "São Paulo",
              "state",
              "SP",
              "postalCode",
              "01001-000"));
    } else fulfillment.put("type", "PICKUP");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", UUID.randomUUID().toString());
    body.put("items", List.of(Map.of("variantId", variant.toString(), "quantity", quantity)));
    body.put("fulfillment", fulfillment);
    body.put(
        "buyer",
        Map.of(
            "name",
            "Comprador Sintético",
            "email",
            "comprador@example.test",
            "phone",
            "(11) 98888-7777"));
    body.put("expectedTotal", total);
    return client.send(
        HttpRequest.newBuilder(uri("/api/public/stores/" + slug + "/orders"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> pay(String slug, String order, String token) throws Exception {
    var request =
        HttpRequest.newBuilder(uri("/api/public/stores/" + slug + "/orders/" + order + "/payment"))
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.noBody());
    if (token != null) request.header("X-Order-Token", token);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String slug, String order, String token) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri("/api/public/stores/" + slug + "/orders/" + order))
            .timeout(Duration.ofSeconds(20))
            .header("X-Order-Token", token)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private UUID store(String slug) {
    UUID tenant = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tenant (id, company_name, created_at) VALUES (?, 'Synthetic company', now())",
        tenant);
    jdbc.update(
        "INSERT INTO identity_user (id, email_original, email_key, status, created_at, updated_at, verified_at) VALUES (?, ?, ?, 'ACTIVE', now(), now(), now())",
        owner,
        slug + "@example.test",
        slug + "@example.test");
    jdbc.update(
        "INSERT INTO tenant_membership (tenant_id, user_id, role, created_at) VALUES (?, ?, 'OWNER', now())",
        tenant,
        owner);
    jdbc.update(
        "INSERT INTO store (id, tenant_id, name, slug, status, created_at) VALUES (?, ?, 'Synthetic store', ?, 'PUBLISHED', now())",
        UUID.randomUUID(),
        tenant,
        slug);
    jdbc.update(
        """
        INSERT INTO store_fulfillment(store_id,tenant_id,pickup_enabled,pickup_instructions,
          fixed_shipping_enabled,fixed_shipping_amount,fixed_shipping_area,updated_at)
        SELECT id,tenant_id,true,'Retirada sintética',true,12.50,'Centro',now()
        FROM store WHERE tenant_id=?
        """,
        tenant);
    return tenant;
  }

  private UUID variant(UUID tenant, String name, String price, String options) {
    UUID product = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind) SELECT ?,?,id,?,?,?::numeric,'BRL',now(),now(),true,'VARIABLE' FROM store WHERE tenant_id=?",
        product,
        tenant,
        name,
        "SKU-" + product.toString().substring(0, 8),
        price,
        tenant);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,?::numeric,NULL,'BRL',?,?)",
        id,
        tenant,
        product,
        "SKU-" + id.toString().substring(0, 8),
        price,
        options,
        id.toString());
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,50)", id, tenant);
    return id;
  }

  private void connect(UUID tenant, String accountId, String accessToken) {
    TokenCipher cipher = providers.requireCipher();
    jdbc.update(
        """
        INSERT INTO payment_account(tenant_id,provider,provider_user_id,status,access_token,
          refresh_token,access_expires_at,key_version,connected_at)
        VALUES (?,'MERCADO_PAGO',?,'CONNECTED',?,?,now()+interval '150 days',?,now())
        """,
        tenant,
        accountId,
        cipher.encrypt(accessToken, tenant, "mp-access-token"),
        cipher.encrypt("TG-synthetic-refresh", tenant, "mp-refresh-token"),
        TokenCipher.KEY_VERSION);
  }
}
