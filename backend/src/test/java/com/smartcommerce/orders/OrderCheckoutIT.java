package com.smartcommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** SC-008: public checkout, idempotency, limits, snapshots and tenant isolation on PostgreSQL. */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orders.checkout.enabled=true",
      "orders.client-ip.secret=" + OrderCheckoutIT.SECRET
    })
class OrderCheckoutIT {
  static final String SECRET = "synthetic-forwarding-secret-for-tests-only-0001";

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String EMAIL = "Comprador.Sintetico@Example.test";
  private static final String PHONE = "(11) 98888-7777";
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  UUID tenantA;
  UUID tenantB;
  UUID simpleA;
  UUID variantA;
  UUID scarceA;
  UUID simpleB;

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, order_rate_limit CASCADE");
    tenantA = store("pedido-a", "PUBLISHED");
    tenantB = store("pedido-b", "PUBLISHED");
    store("pedido-rascunho", "DRAFT");
    delivery(tenantA, "Rua Sintética, 10 — seg. a sex.", "12.50", "Centro (área informativa)");
    delivery(tenantB, "Retirada B", null, null);
    simpleA = simple(tenantA, "SKU-A", "15.90", "3.00", 7, true);
    UUID variable = product(tenantA, "Camiseta", "SKU-VAR", "20.00", "VARIABLE", true);
    variantA =
        variant(
            tenantA,
            variable,
            UUID.randomUUID(),
            "SKU-AZUL",
            "20.00",
            null,
            "[{\"name\":\"Cor\",\"value\":\"Azul\"}]",
            50);
    scarceA = simple(tenantA, "SKU-LAST", "9.99", "1.00", 1, true);
    simpleB = simple(tenantB, "SKU-B", "5.00", "1.00", 50, true);
  }

  @Test
  void createsPendingOrderWithSnapshotsReservationAndOneTimeToken() throws Exception {
    var created =
        post(
            "pedido-a",
            body(
                UUID.randomUUID(),
                lines(simpleA, 2, variantA, 1),
                shipping(),
                "64.30")); // 2 × 15.90 + 20.00 + 12.50
    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(created.headers().allValues("Set-Cookie")).isEmpty();
    JsonNode result = JSON.readTree(created.body());
    assertThat(result.get("replayed").asBoolean()).isFalse();
    String token = result.get("accessToken").asString();
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    JsonNode order = result.get("order");
    assertThat(order.get("number").asLong()).isEqualTo(1);
    assertThat(order.get("status").asString()).isEqualTo("PENDING_PAYMENT");
    assertThat(order.get("subtotal").asString()).isEqualTo("51.80");
    assertThat(order.get("shipping").asString()).isEqualTo("12.50");
    assertThat(order.get("total").asString()).isEqualTo("64.30");
    assertThat(order.get("fulfillment").get("type").asString()).isEqualTo("FIXED_SHIPPING");
    assertThat(order.get("fulfillment").get("text").asString())
        .isEqualTo("Centro (área informativa)");
    assertThat(order.get("items")).hasSize(2);
    assertThat(order.get("items").get(0).get("lineTotal").asString()).isEqualTo("31.80");
    assertThat(order.get("items").get(1).get("options"))
        .isEqualTo(JSON.readTree("[{\"name\":\"Cor\",\"value\":\"Azul\"}]"));
    Instant expires = Instant.parse(order.get("reservationExpiresAt").asString());
    assertThat(expires)
        .isBetween(Instant.now().plus(Duration.ofMinutes(29)), Instant.now().plusSeconds(1801));
    assertThat(created.body())
        .doesNotContain("SKU-", "\"3.00\"", "cost", "sku", "tenant", "store", "Sintetico", "98888");
    UUID id = UUID.fromString(order.get("id").asString());

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT access_token_hash,buyer_email,buyer_email_key,buyer_phone,address_postal_code,"
                + "reservation_expires_at FROM sales_order WHERE id=?",
            id);
    assertThat(row.get("access_token_hash")).isEqualTo(sha256(token)).isNotEqualTo(token);
    assertThat(row.get("buyer_email")).isEqualTo(EMAIL);
    assertThat(row.get("buyer_email_key")).isEqualTo("comprador.sintetico@example.test");
    assertThat(row.get("buyer_phone")).isEqualTo("11988887777");
    assertThat(row.get("address_postal_code")).isEqualTo("01001000");
    assertThat(
            jdbc.queryForList(
                "SELECT unit_cost_snapshot FROM sales_order_item WHERE order_id=? ORDER BY position",
                id))
        .extracting(
            item ->
                item.get("unit_cost_snapshot") == null
                    ? null
                    : item.get("unit_cost_snapshot").toString())
        .containsExactly("3.00", null);
    assertThat(
            jdbc.queryForList(
                "SELECT variant_id,quantity,status FROM inventory_reservation WHERE order_id=? ORDER BY quantity",
                id))
        .hasSize(2)
        .allSatisfy(reservation -> assertThat(reservation.get("status")).isEqualTo("ACTIVE"));
    assertThat(quoteStatus("pedido-a", simpleA, 6)).isEqualTo("INSUFFICIENT"); // 7 − 2 reserved

    var pickup =
        post(
            "pedido-a",
            body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90"));
    assertThat(pickup.statusCode()).isEqualTo(201);
    JsonNode second = JSON.readTree(pickup.body()).get("order");
    assertThat(second.get("number").asLong()).isEqualTo(2);
    assertThat(second.get("shipping").asString()).isEqualTo("0.00");
    assertThat(second.get("fulfillment").get("text").asString())
        .isEqualTo("Rua Sintética, 10 — seg. a sex.");
    assertThat(
            jdbc.queryForObject(
                "SELECT address_line1 FROM sales_order WHERE id=?",
                String.class,
                UUID.fromString(second.get("id").asString())))
        .isNull();
  }

  @Test
  void buyerReadsRequireTheTokenAndTheOrdersOwnStore() throws Exception {
    var created =
        JSON.readTree(
            post(
                    "pedido-a",
                    body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90"))
                .body());
    String id = created.get("order").get("id").asString();
    String token = created.get("accessToken").asString();
    var found = get("pedido-a", id, token);
    assertThat(found.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(found.body())).isEqualTo(created.get("order"));
    assertThat(found.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    String wrong = token.substring(0, 42) + (token.endsWith("A") ? "B" : "A");
    for (var attempt :
        List.of(
            get("pedido-a", id, wrong),
            get("pedido-a", id, null),
            get("pedido-b", id, token),
            get("pedido-a", UUID.randomUUID().toString(), token),
            get("pedido-a", "not-a-uuid", token))) assertThat(attempt.statusCode()).isEqualTo(404);

    // Snapshots are immutable: later catalog changes never alter the order.
    jdbc.update("UPDATE catalog_variant SET price=99.99, cost=50.00 WHERE id=?", simpleA);
    jdbc.update("UPDATE catalog_product SET name='Renomeado' WHERE id=?", simpleA);
    JsonNode again = JSON.readTree(get("pedido-a", id, token).body());
    assertThat(again.get("items").get(0).get("unitPrice").asString()).isEqualTo("15.90");
    assertThat(again.get("items").get(0).get("productName").asString()).isEqualTo("Produto");

    // A pending order past its reservation deadline reads EXPIRED without any routine.
    jdbc.update(
        "UPDATE sales_order SET created_at=now()-interval '2 hours',"
            + " reservation_expires_at=now()-interval '1 second' WHERE id=?",
        UUID.fromString(id));
    assertThat(JSON.readTree(get("pedido-a", id, token).body()).get("status").asString())
        .isEqualTo("EXPIRED");
  }

  @Test
  void sameRequestIdReplaysWithoutSecondReservationAndRejectsOtherPayloads() throws Exception {
    UUID request = UUID.randomUUID();
    String payload = body(request, lines(simpleA, 2), Map.of("type", "PICKUP"), "31.80");
    var first = post("pedido-a", payload);
    assertThat(first.statusCode()).isEqualTo(201);
    var replay = post("pedido-a", payload);
    assertThat(replay.statusCode()).isEqualTo(200);
    JsonNode replayed = JSON.readTree(replay.body());
    assertThat(replayed.get("replayed").asBoolean()).isTrue();
    assertThat(replayed.get("accessToken").isNull()).isTrue();
    assertThat(replayed.get("order")).isEqualTo(JSON.readTree(first.body()).get("order"));
    assertThat(count("sales_order")).isEqualTo(1);
    assertThat(count("inventory_reservation")).isEqualTo(1);
    var conflict =
        post("pedido-a", body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90"));
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(conflict.body()).get("code").asString()).isEqualTo("REQUEST_CONFLICT");
    // Request keys are per tenant: the same key creates an independent order in store B.
    var other =
        post("pedido-b", body(request, lines(simpleB, 1), Map.of("type", "PICKUP"), "5.00"));
    assertThat(other.statusCode()).isEqualTo(201);
    assertThat(count("sales_order")).isEqualTo(2);
  }

  @Test
  void rejectedCheckoutsWriteNothingAndDoNotConsumeTheRequestKey() throws Exception {
    UUID request = UUID.randomUUID();
    var changed =
        post("pedido-a", body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "14.90"));
    assertThat(changed.statusCode()).isEqualTo(409);
    JsonNode price = JSON.readTree(changed.body());
    assertThat(price.get("code").asString()).isEqualTo("PRICE_CHANGED");
    assertThat(price.get("currentTotal").asString()).isEqualTo("15.90");
    var stock =
        post("pedido-a", body(request, lines(scarceA, 2), Map.of("type", "PICKUP"), "19.98"));
    assertThat(stock.statusCode()).isEqualTo(409);
    assertThat(JSON.readTree(stock.body()).get("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
    assertThat(stock.body()).doesNotContain("\"1\"", "quantity");
    for (UUID unavailable : List.of(simpleB, UUID.randomUUID(), unpublished()))
      assertThat(
              code(
                  post(
                      "pedido-a",
                      body(request, lines(unavailable, 1), Map.of("type", "PICKUP"), "5.00"))))
          .isEqualTo("ITEM_UNAVAILABLE");
    assertThat(
            code(post("pedido-b", body(UUID.randomUUID(), lines(simpleB, 1), shipping(), "17.50"))))
        .isEqualTo("FULFILLMENT_UNAVAILABLE");
    assertThat(count("sales_order") + count("sales_order_item") + count("inventory_reservation"))
        .isZero();
    var accepted =
        post("pedido-a", body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90"));
    assertThat(accepted.statusCode()).isEqualTo(201);
  }

  @Test
  void invalidBodiesUnknownStoresAndDraftsNeverCreateOrders() throws Exception {
    UUID request = UUID.randomUUID();
    Map<String, Object> pickupWithAddress = new LinkedHashMap<>(shipping());
    pickupWithAddress.put("type", "PICKUP");
    List<String> invalid =
        List.of(
            "{}",
            "not json",
            body(request, lines(simpleA, 1), pickupWithAddress, "15.90"),
            body(request, lines(simpleA, 1), Map.of("type", "FIXED_SHIPPING"), "28.40"),
            body(request, lines(simpleA, 11), Map.of("type", "PICKUP"), "174.90"),
            body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "15.9000"),
            body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "-15.90"),
            body(request, lines(simpleA, 1), Map.of("type", "TELEPORT"), "15.90"),
            body(request, lines(simpleA, 1, simpleA, 1), Map.of("type", "PICKUP"), "31.80"),
            withBuyer(request, "Nome", "sem-arroba", PHONE),
            withBuyer(request, "Nome", EMAIL, "123"),
            withBuyer(request, "Linha\nquebrada", EMAIL, PHONE),
            body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90")
                .replace("\"expectedTotal\"", "\"extra\":1,\"expectedTotal\""));
    for (String payload : invalid)
      assertThat(post("pedido-a", payload).statusCode()).as(payload).isEqualTo(400);
    List<Map<String, Object>> many = new ArrayList<>();
    for (int i = 0; i < 21; i++)
      many.add(Map.of("variantId", UUID.randomUUID().toString(), "quantity", 1));
    assertThat(post("pedido-a", body(request, many, Map.of("type", "PICKUP"), "1.00")).statusCode())
        .isEqualTo(400);
    assertThat(post("pedido-a", "{\"x\":\"" + "a".repeat(9000) + "\"}").statusCode())
        .isEqualTo(413);
    String valid = body(request, lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90");
    assertThat(post("pedido-rascunho", valid).statusCode()).isEqualTo(404);
    assertThat(post("pedido-inexistente", valid).statusCode()).isEqualTo(404);
    assertThat(post("Pedido_A", valid).statusCode()).isEqualTo(404);
    assertThat(count("sales_order")).isZero();
  }

  @Test
  void exactlyOneOfManyConcurrentCheckoutsGetsTheLastUnit() throws Exception {
    int threads = 8;
    var barrier = new CyclicBarrier(threads);
    var tasks = new ArrayList<Callable<String>>();
    for (int i = 0; i < threads; i++) {
      String payload = body(UUID.randomUUID(), lines(scarceA, 1), Map.of("type", "PICKUP"), "9.99");
      tasks.add(
          () -> {
            barrier.await(20, TimeUnit.SECONDS);
            var response = post("pedido-a", payload);
            return response.statusCode() == 201 ? "OK" : code(response);
          });
    }
    List<String> outcomes = run(tasks);
    assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
    assertThat(outcomes).filteredOn("INSUFFICIENT_STOCK"::equals).hasSize(threads - 1);
    assertThat(count("sales_order")).isEqualTo(1);
    assertThat(count("inventory_reservation")).isEqualTo(1);
  }

  @Test
  void concurrentRetriesOfOneRequestCreateASingleOrder() throws Exception {
    int threads = 6;
    String payload = body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90");
    var barrier = new CyclicBarrier(threads);
    var tasks = new ArrayList<Callable<String>>();
    for (int i = 0; i < threads; i++)
      tasks.add(
          () -> {
            barrier.await(20, TimeUnit.SECONDS);
            return String.valueOf(post("pedido-a", payload).statusCode());
          });
    List<String> outcomes = run(tasks);
    assertThat(outcomes).filteredOn("201"::equals).hasSize(1);
    assertThat(outcomes).filteredOn("200"::equals).hasSize(threads - 1);
    assertThat(count("sales_order")).isEqualTo(1);
    assertThat(count("inventory_reservation")).isEqualTo(1);
  }

  @Test
  void concurrentCheckoutsAboveThePoolSizeNeverWaitForASecondConnection() throws Exception {
    // Hikari keeps 10 connections; each checkout must hold at most one at a time (PR #8 review).
    int threads = 16;
    var barrier = new CyclicBarrier(threads);
    var tasks = new ArrayList<Callable<String>>();
    for (int i = 0; i < threads; i++) {
      String payload =
          payload(
              UUID.randomUUID(),
              lines(variantA, 1),
              Map.of("type", "PICKUP"),
              "Comprador Sintético",
              "comprador" + i + "@example.test",
              PHONE,
              "20.00");
      tasks.add(
          () -> {
            barrier.await(20, TimeUnit.SECONDS);
            return String.valueOf(post("pedido-a", payload).statusCode());
          });
    }
    assertThat(run(tasks)).containsOnly("201");
    assertThat(count("sales_order")).isEqualTo(threads);
  }

  @Test
  void retriesOfACommittedRequestDoNotUseThePerIpAttempts() throws Exception {
    String payload = body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90");
    assertThat(postFrom("203.0.113.8", true, payload).statusCode()).isEqualTo(201);
    for (int i = 0; i < 25; i++)
      assertThat(postFrom("203.0.113.8", true, payload).statusCode()).isEqualTo(200);
    String changed =
        body(
            UUID.fromString(JSON.readTree(payload).get("requestId").asString()),
            lines(simpleA, 2),
            Map.of("type", "PICKUP"),
            "31.80");
    assertThat(code(postFrom("203.0.113.8", true, changed))).isEqualTo("REQUEST_CONFLICT");
    // Only the creating attempt was charged: a new attempt from the same IP is still admitted.
    String rejected = body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "1.00");
    assertThat(code(postFrom("203.0.113.8", true, rejected))).isEqualTo("PRICE_CHANGED");
    assertThat(count("sales_order")).isEqualTo(1);
  }

  @Test
  void pendingOrdersPerEmailAreLimitedPerStore() throws Exception {
    for (int i = 0; i < 5; i++)
      assertThat(
              post(
                      "pedido-a",
                      body(
                          UUID.randomUUID(), lines(variantA, 1), Map.of("type", "PICKUP"), "20.00"))
                  .statusCode())
          .isEqualTo(201);
    var sixth =
        post("pedido-a", withBuyer(UUID.randomUUID(), "Outro nome", EMAIL.toLowerCase(), PHONE));
    assertThat(sixth.statusCode()).isEqualTo(429);
    assertThat(sixth.body()).doesNotContain("comprador", "example");
    assertThat(
            post(
                    "pedido-a",
                    withBuyer(UUID.randomUUID(), "Outra pessoa", "outra@example.test", PHONE))
                .statusCode())
        .isEqualTo(201);
    // The limit is per store: the same e-mail still buys in store B.
    assertThat(
            post(
                    "pedido-b",
                    body(UUID.randomUUID(), lines(simpleB, 1), Map.of("type", "PICKUP"), "5.00"))
                .statusCode())
        .isEqualTo(201);
    // Expired pending orders no longer count.
    jdbc.update(
        "UPDATE sales_order SET created_at=now()-interval '2 hours',"
            + " reservation_expires_at=now()-interval '1 second'");
    assertThat(
            post(
                    "pedido-a",
                    body(UUID.randomUUID(), lines(variantA, 1), Map.of("type", "PICKUP"), "20.00"))
                .statusCode())
        .isEqualTo(201);
  }

  @Test
  void attemptsPerSignedClientIpAreLimitedAndUnsignedHeadersAreIgnored(CapturedOutput output)
      throws Exception {
    String rejected = body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "1.00");
    for (int i = 0; i < 20; i++)
      assertThat(postFrom("203.0.113.7", true, rejected).statusCode()).isEqualTo(409);
    var limited = postFrom("203.0.113.7", true, rejected);
    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(limited.headers().firstValue("Retry-After")).contains("3600");
    String valid = body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "15.90");
    // A forged (unsigned) header falls back to the connection address, which is not limited.
    assertThat(postFrom("203.0.113.7", false, valid).statusCode()).isEqualTo(201);
    assertThat(postFrom("198.51.100.9", true, rejected).statusCode()).isEqualTo(409);
    // Keys are HMAC-SHA256 hex digests: the IP is never stored in clear. Checking digit substrings
    // such as "203" would be flaky, since a random hex digest contains them about 1.5% of the time.
    assertThat(jdbc.queryForList("SELECT bucket_key FROM order_rate_limit", String.class))
        .isNotEmpty()
        .allSatisfy(
            key ->
                assertThat(key)
                    .matches("[0-9a-f]{64}")
                    .doesNotContain("203.0.113.7", "198.51.100.9", "127.0.0.1"));
    assertThat(output.getAll())
        .doesNotContain(EMAIL, "comprador.sintetico", "98888", "11988887777", "203.0.113.7");
  }

  @Test
  void signedIpv6AddressesOfOneSlash64ShareTheAttemptLimit() throws Exception {
    // One host usually owns a whole /64: rotating addresses inside it must not reset the limit.
    String rejected = body(UUID.randomUUID(), lines(simpleA, 1), Map.of("type", "PICKUP"), "1.00");
    for (int i = 1; i <= 20; i++)
      assertThat(postFrom("2001:db8:7:9::" + Integer.toHexString(i), true, rejected).statusCode())
          .isEqualTo(409);
    assertThat(postFrom("2001:db8:7:9:ffff::1", true, rejected).statusCode()).isEqualTo(429);
    assertThat(postFrom("::ffff:203.0.113.50", true, rejected).statusCode()).isEqualTo(409);
    assertThat(postFrom("2001:db8:7:a::1", true, rejected).statusCode()).isEqualTo(409);
  }

  // --- helpers ---------------------------------------------------------------------------

  private List<String> run(List<Callable<String>> tasks) throws Exception {
    List<String> outcomes = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(tasks.size())) {
      for (var future : pool.invokeAll(tasks, 90, TimeUnit.SECONDS)) outcomes.add(future.get());
    }
    return outcomes;
  }

  private static List<Map<String, Object>> lines(Object... pairs) {
    List<Map<String, Object>> lines = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2)
      lines.add(Map.of("variantId", pairs[i].toString(), "quantity", pairs[i + 1]));
    return lines;
  }

  private static Map<String, Object> shipping() {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("line1", "Rua Sintética, 100");
    address.put("line2", "Apto 1");
    address.put("district", "Centro");
    address.put("city", "São Paulo");
    address.put("state", "sp");
    address.put("postalCode", "01001-000");
    Map<String, Object> fulfillment = new LinkedHashMap<>();
    fulfillment.put("type", "FIXED_SHIPPING");
    fulfillment.put("address", address);
    return fulfillment;
  }

  private static String body(
      UUID request,
      List<Map<String, Object>> lines,
      Map<String, Object> fulfillment,
      String total) {
    return payload(request, lines, fulfillment, "Comprador Sintético", EMAIL, PHONE, total);
  }

  private String withBuyer(UUID request, String name, String email, String phone) {
    return payload(
        request, lines(variantA, 1), Map.of("type", "PICKUP"), name, email, phone, "20.00");
  }

  private static String payload(
      UUID request,
      List<Map<String, Object>> lines,
      Map<String, Object> fulfillment,
      String name,
      String email,
      String phone,
      String total) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", request.toString());
    body.put("items", lines);
    body.put("fulfillment", fulfillment);
    body.put("buyer", Map.of("name", name, "email", email, "phone", phone));
    body.put("expectedTotal", total);
    return JSON.writeValueAsString(body);
  }

  private HttpResponse<String> post(String slug, String body) throws Exception {
    return send(slug, body, null, false);
  }

  private HttpResponse<String> postFrom(String ip, boolean signed, String body) throws Exception {
    return send("pedido-a", body, ip, signed);
  }

  private HttpResponse<String> send(String slug, String body, String ip, boolean signed)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/public/stores/" + slug + "/orders"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (ip != null) {
      String timestamp = String.valueOf(Instant.now().getEpochSecond());
      request
          .header("X-SC-Client-IP", ip)
          .header("X-SC-Client-IP-Timestamp", timestamp)
          .header(
              "X-SC-Client-IP-Signature", signed ? hmac(ip + "\n" + timestamp) : "0".repeat(64));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(String slug, String order, String token) throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:" + port + "/api/public/stores/" + slug + "/orders/" + order))
            .timeout(Duration.ofSeconds(20))
            .GET();
    if (token != null) request.header("X-Order-Token", token);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String quoteStatus(String slug, UUID variant, int quantity) throws Exception {
    var response =
        client.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://127.0.0.1:" + port + "/api/public/stores/" + slug + "/cart/quote"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(
                            Map.of(
                                "items",
                                List.of(
                                    Map.of(
                                        "variantId", variant.toString(), "quantity", quantity))))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return JSON.readTree(response.body()).get("lines").get(0).get("status").asString();
  }

  private static String code(HttpResponse<String> response) {
    return JSON.readTree(response.body()).get("code").asString();
  }

  private long count(String table) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    return count == null ? -1 : count;
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String hmac(String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
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

  private void delivery(UUID tenant, String pickup, String amount, String area) {
    jdbc.update(
        """
        INSERT INTO store_fulfillment(store_id,tenant_id,pickup_enabled,pickup_instructions,
          fixed_shipping_enabled,fixed_shipping_amount,fixed_shipping_area,updated_at)
        SELECT id,tenant_id,?,?,?,?::numeric,?,now() FROM store WHERE tenant_id=?
        """,
        pickup != null,
        pickup,
        amount != null,
        amount,
        area,
        tenant);
  }

  private UUID unpublished() {
    return simple(tenantA, "SKU-HIDDEN", "5.00", null, 9, false);
  }

  private UUID simple(
      UUID tenant, String sku, String price, String cost, int stock, boolean published) {
    UUID product = product(tenant, "Produto", sku, price, "SIMPLE", published);
    return variant(tenant, product, product, sku, price, cost, "[]", stock);
  }

  private UUID product(
      UUID tenant, String name, String sku, String price, String kind, boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind) SELECT ?,?,id,?,?,?::numeric,'BRL',now(),now(),?,? FROM store WHERE tenant_id=?",
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
      UUID tenant,
      UUID product,
      UUID id,
      String sku,
      String price,
      String cost,
      String options,
      int stock) {
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,?::numeric,?::numeric,'BRL',?,?)",
        id,
        tenant,
        product,
        sku,
        price,
        cost,
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
