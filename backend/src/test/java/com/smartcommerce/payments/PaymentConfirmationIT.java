package com.smartcommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcommerce.orders.domain.CancelReason;
import com.smartcommerce.orders.domain.OrderException;
import com.smartcommerce.orders.dto.response.AdminOrderView;
import com.smartcommerce.orders.services.OrderAdminService;
import com.smartcommerce.orders.services.OrderLifecycleService;
import com.smartcommerce.orders.services.OrderRefundService;
import com.smartcommerce.payments.provider.PaymentProviders;
import com.smartcommerce.payments.security.TokenCipher;
import com.smartcommerce.payments.services.PaymentEventService;
import com.smartcommerce.payments.services.PaymentReconciliationService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 * SC-014 against a loopback fake of Mercado Pago's Orders API: signed webhook, idempotent event
 * record, confirmation only by querying the provider with the order's own store token, a single
 * stock deduction by the system actor, late payment (P4) with and without stock, races with the
 * expiration batch and between events, and A/B isolation.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "orders.checkout.enabled=true",
      "deployment.environment=hosted-test",
      "orders.client-ip.secret=synthetic-forwarding-secret-for-tests-only-0014",
      "orders.maintenance.enabled=false",
      "payments.token-refresh.enabled=false",
      "payments.events.enabled=false",
      "payments.reconciliation.enabled=false"
    })
class PaymentConfirmationIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse(
                  "postgres@sha256:afc7e2d441324c0388fa80c3d24f733b4194a4eb7f47dd8ee2b08eb1a24a647c")
              .asCompatibleSubstituteFor("postgres"));

  private static final JsonMapper JSON = JsonMapper.builder().build();
  static final String WEBHOOK_SECRET = "synthetic-webhook-secret-0014";
  private static final String TOKEN_A = "APP_USR-synthetic-seller-a-0014";
  private static final String TOKEN_B = "APP_USR-synthetic-seller-b-0014";
  private static final Map<String, String> SELLERS = Map.of(TOKEN_A, "111111", TOKEN_B, "222222");
  private static final List<String> READS = new CopyOnWriteArrayList<>();
  // Provider-side state of each order, by id; tests change status to simulate the buyer paying.
  private static final Map<String, Map<String, Object>> ORDERS = new ConcurrentHashMap<>();
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  // Simulates the provider being down for order reads (SC-015).
  private static final AtomicBoolean DOWN = new AtomicBoolean();
  // SC-016: refund requests seen by the fake (token and idempotency key) and how it answers.
  private static final List<String> REFUNDS = new CopyOnWriteArrayList<>();
  private static final AtomicReference<String> REFUND_MODE = new AtomicReference<>("PENDING");
  private static final HttpServer PROVIDER = provider();

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PaymentProviders providers;
  @Autowired PaymentEventService events;
  @Autowired OrderLifecycleService lifecycle;
  @Autowired PaymentReconciliationService reconciliation;
  @Autowired OrderAdminService admin;
  @Autowired OrderRefundService refunds;
  UUID tenantA;
  UUID tenantB;
  UUID variantA;
  UUID variantB;

  private static HttpServer provider() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/v1/orders",
          exchange -> {
            String token =
                exchange
                    .getRequestHeaders()
                    .getFirst("Authorization")
                    .substring("Bearer ".length());
            Map<String, Object> reply;
            int status = 200;
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/refund")) {
              String id =
                  path.substring("/v1/orders/".length(), path.length() - "/refund".length());
              REFUNDS.add(token + " " + exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
              Map<String, Object> state = ORDERS.get(id);
              switch (REFUND_MODE.get()) {
                case "FAIL" -> {
                  status = 500;
                  reply = Map.of("message", "internal_error");
                }
                case "ALREADY" -> {
                  state.put("status_detail", "refunded");
                  status = 409;
                  reply =
                      Map.of(
                          "code", "order_already_refunded", "message", "Order already refunded.");
                }
                case "IMMEDIATE" -> {
                  state.put("status_detail", "refunded");
                  status = 201;
                  reply = state;
                }
                default -> {
                  status = 201;
                  reply = state; // accepted, confirmed later
                }
              }
            } else if ("POST".equals(exchange.getRequestMethod())) {
              JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
              String id = "ORD" + SEQUENCE.incrementAndGet();
              reply = new LinkedHashMap<>();
              reply.put("id", id);
              reply.put("status", "created");
              reply.put("status_detail", "created");
              reply.put("checkout_url", "https://sandbox.mercadopago.com.br/checkout?id=" + id);
              reply.put("total_amount", body.get("total_amount").asString());
              reply.put("total_paid_amount", "0.00");
              reply.put("currency", "BRL");
              reply.put("external_reference", body.get("external_reference").asString());
              reply.put("user_id", SELLERS.get(token));
              ORDERS.put(id, reply);
              status = 201;
            } else {
              String id = exchange.getRequestURI().getPath().substring("/v1/orders/".length());
              READS.add(token + " " + id);
              reply = ORDERS.get(id);
              if (DOWN.get()) {
                status = 500;
                reply = Map.of("message", "internal_error");
              } else
              // Another seller's token cannot read the order.
              if (reply == null || !SELLERS.get(token).equals(reply.get("user_id"))) {
                status = 404;
                reply = Map.of("message", "not found");
              }
            }
            byte[] bytes = JSON.writeValueAsBytes(reply);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
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
    registry.add("payments.mercado-pago.webhook-secret", () -> WEBHOOK_SECRET);
    registry.add(
        "payments.token-encryption-key",
        () -> Base64.getEncoder().encodeToString("synthetic-32-byte-test-key-00014".getBytes()));
  }

  @AfterAll
  static void stopProvider() {
    PROVIDER.stop(0);
  }

  @BeforeEach
  void resetDisposableFixtures() {
    // TRUNCATE is restricted to this class's disposable container, never the developer database.
    jdbc.execute("TRUNCATE tenant, identity_user, order_rate_limit, payment_event CASCADE");
    READS.clear();
    ORDERS.clear();
    DOWN.set(false);
    REFUNDS.clear();
    REFUND_MODE.set("PENDING");
    tenantA = store("confirma-a");
    tenantB = store("confirma-b");
    variantA = variant(tenantA, "10.00", 10);
    variantB = variant(tenantB, "5.00", 10);
    connect(tenantA, "111111", TOKEN_A);
    connect(tenantB, "222222", TOKEN_B);
  }

  @Test
  void signedWebhookConfirmsOnceAndDeductsStockOnceBySystemActor(CapturedOutput output)
      throws Exception {
    Order order = paidLater("confirma-a", variantA, 2); // 2 × 10.00
    approve(order);

    var received = webhook(order.provider(), "evt-1", "req-1");
    assertThat(received.statusCode()).isEqualTo(200);
    assertThat(eventStatus("req-1")).isEqualTo("PENDING");
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT"); // nothing before processing
    assertThat(events.processDue(20)).isEqualTo(1);

    assertThat(orderStatus(order)).isEqualTo("PAID");
    assertThat(eventStatus("req-1")).isEqualTo("PROCESSED");
    assertThat(READS).containsExactly(TOKEN_A + " " + order.provider());
    assertThat(balance(tenantA, variantA)).isEqualTo(8);
    assertThat(sales(order))
        .singleElement()
        .satisfies(
            sale -> {
              assertThat(sale.get("delta")).isEqualTo(-2);
              assertThat(sale.get("balance_after")).isEqualTo(8);
              assertThat(sale.get("actor_kind")).isEqualTo("SYSTEM");
              assertThat(sale.get("actor_id")).isNull();
              assertThat(sale.get("reason").toString()).startsWith("Venda do pedido nº ");
            });
    assertThat(reservationStatuses(order)).containsOnly("CONSUMED");
    Map<String, Object> payment =
        jdbc.queryForMap(
            "SELECT status,paid_amount,approved_at FROM payment WHERE order_id=?", order.id());
    assertThat(payment.get("status")).isEqualTo("APPROVED");
    assertThat(payment.get("paid_amount").toString()).isEqualTo("20.00");
    assertThat(payment.get("approved_at")).isNotNull();

    // The same signed request again (even with another body id) is one event; a new request
    // about the same order is a new event that changes nothing.
    assertThat(webhook(order.provider(), "evt-replayed", "req-1").statusCode()).isEqualTo(200);
    assertThat(eventCount()).isEqualTo(1);
    assertThat(webhook(order.provider(), "evt-2", "req-3").statusCode()).isEqualTo(200);
    assertThat(events.processDue(20)).isEqualTo(1);
    assertThat(eventStatus("req-3")).isEqualTo("PROCESSED");
    assertThat(READS).hasSize(1); // already approved: the provider is not even queried again
    assertThat(balance(tenantA, variantA)).isEqualTo(8);
    assertThat(sales(order)).hasSize(1);

    JsonNode buyer = JSON.readTree(get("confirma-a", order).body());
    assertThat(buyer.get("status").asString()).isEqualTo("PAID");
    assertThat(buyer.get("payable").asBoolean()).isFalse();
    assertThat(output.getAll()).doesNotContain(TOKEN_A, WEBHOOK_SECRET);
  }

  @Test
  void forgedStaleOrMalformedNotificationsAreRejectedAndRecordNothing() throws Exception {
    Order order = paidLater("confirma-a", variantA, 1);
    approve(order);
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String body = body(order.provider(), "evt-x");
    String good =
        WebhookSignatureTest.sign(WEBHOOK_SECRET, manifest(order.provider(), "req-x", ts));
    for (var attempt :
        List.of(
            post(order.provider(), body, "req-x", null),
            post(order.provider(), body, "req-x", "ts=" + ts + ",v1=" + "0".repeat(64)),
            post(
                order.provider(),
                body,
                "req-x",
                "ts="
                    + ts
                    + ",v1="
                    + WebhookSignatureTest.sign(
                        "another-synthetic-secret", manifest(order.provider(), "req-x", ts))),
            post(order.provider(), body, "req-other", "ts=" + ts + ",v1=" + good),
            post("ORD999", body(("ORD999"), "evt-x"), "req-x", "ts=" + ts + ",v1=" + good),
            post(
                order.provider(),
                body,
                "req-old",
                stale(order.provider(), "req-old", Instant.now().minusSeconds(1200)))))
      // 429 once the shared failure window is spent (another test floods it); both are refusals.
      assertThat(attempt.statusCode()).isIn(401, 429);

    // Signed for this order, but the body names another one: rejected, not routed.
    var swapped =
        post(
            order.provider(),
            body("ORD777", "evt-y"),
            "req-y",
            signature(order.provider(), "req-y"));
    assertThat(swapped.statusCode()).isEqualTo(400);
    var huge =
        post(
            order.provider(),
            "{\"pad\":\"" + "x".repeat(20_000) + "\"}",
            "req-z",
            signature(order.provider(), "req-z"));
    assertThat(huge.statusCode()).isEqualTo(413);
    assertThat(eventCount()).isZero();
    assertThat(events.processDue(20)).isZero();
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    assertThat(READS).isEmpty();
  }

  @Test
  void outOfOrderNotificationsOnlyConfirmWhatTheProviderReportsAsPaid() throws Exception {
    Order order = paidLater("confirma-a", variantA, 1);
    ORDERS.get(order.provider()).put("status", "action_required");
    webhook(order.provider(), "evt-late-first", "req-1");
    events.processDue(20);
    assertThat(eventStatus("req-1")).isEqualTo("PROCESSED");
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    assertThat(balance(tenantA, variantA)).isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT provider_status FROM payment WHERE order_id=?", String.class, order.id()))
        .isEqualTo("action_required");

    // Processed but only partly paid: still not confirmed.
    ORDERS
        .get(order.provider())
        .putAll(
            Map.of(
                "status", "processed", "status_detail", "accredited", "total_paid_amount", "5.00"));
    webhook(order.provider(), "evt-partial", "req-2");
    events.processDue(20);
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");

    approve(order);
    webhook(order.provider(), "evt-paid", "req-3");
    events.processDue(20);
    assertThat(orderStatus(order)).isEqualTo("PAID");
    assertThat(balance(tenantA, variantA)).isEqualTo(9);
  }

  @Test
  void latePaymentReservesAgainOrIsFlaggedPaidWithoutStock() throws Exception {
    // Expired with stock still free: reserved again and deducted (P4).
    Order expired = paidLater("confirma-a", variantA, 3);
    expire(expired);
    assertThat(orderStatus(expired)).isEqualTo("EXPIRED");
    approve(expired);
    webhook(expired.provider(), "evt-exp", "req-1");
    events.processDue(20);
    assertThat(orderStatus(expired)).isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "SELECT expired_at IS NOT NULL FROM sales_order WHERE id=?",
                Boolean.class,
                expired.id()))
        .isTrue();
    assertThat(balance(tenantA, variantA)).isEqualTo(7);
    assertThat(sales(expired)).hasSize(1);

    // Cancelled by the store and paid anyway, with stock: deducted, the cancellation kept.
    Order cancelled = paidLater("confirma-a", variantA, 1);
    cancel(cancelled);
    approve(cancelled);
    webhook(cancelled.provider(), "evt-can", "req-2");
    events.processDue(20);
    assertThat(orderStatus(cancelled)).isEqualTo("PAID");
    assertThat(balance(tenantA, variantA)).isEqualTo(6);

    // Expired, then another buyer reserves every unit: paid without stock, nothing deducted.
    Order unlucky = paidLater("confirma-a", variantA, 2);
    expire(unlucky);
    Order other = paidLater("confirma-a", variantA, 6);
    approve(unlucky);
    webhook(unlucky.provider(), "evt-none", "req-3");
    events.processDue(20);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT status,paid_without_stock FROM sales_order WHERE id=?", unlucky.id());
    assertThat(row.get("status")).isEqualTo("PAID");
    assertThat(row.get("paid_without_stock")).isEqualTo(true);
    assertThat(balance(tenantA, variantA)).isEqualTo(6);
    assertThat(sales(unlucky)).isEmpty();
    assertThat(reservationStatuses(other)).containsOnly("ACTIVE");
    assertThat(JSON.readTree(adminDetailJson(unlucky)).get("paid_without_stock").asBoolean())
        .isTrue();
  }

  @Test
  void racesWithExpirationAndBetweenEventsDeductExactlyOnce() throws Exception {
    for (int round = 0; round < 4; round++) {
      Order order = paidLater("confirma-a", variantA, 1);
      // Due for expiry and paid at the same time.
      jdbc.update(
          "UPDATE sales_order SET created_at=now()-interval '2 hours',"
              + " reservation_expires_at=now()-interval '1 second' WHERE id=?",
          order.id());
      jdbc.update(
          "UPDATE inventory_reservation SET created_at=now()-interval '2 hours',"
              + " expires_at=now()-interval '1 second' WHERE order_id=?",
          order.id());
      approve(order);
      webhook(order.provider(), "evt-r" + round + "a", "req-a" + round);
      webhook(order.provider(), "evt-r" + round + "b", "req-b" + round);
      CyclicBarrier barrier = new CyclicBarrier(3);
      List<Callable<Integer>> work = new ArrayList<>();
      work.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return events.processDue(1);
          });
      work.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return events.processDue(1);
          });
      work.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return lifecycle.expireDue(10);
          });
      try (var executor = Executors.newFixedThreadPool(3)) {
        for (var result : executor.invokeAll(work)) result.get();
      }
      events.processDue(20); // whatever a skipped claim left behind
      assertThat(orderStatus(order)).isEqualTo("PAID");
      assertThat(sales(order)).hasSize(1);
      assertThat(reservationStatuses(order)).doesNotContain("ACTIVE");
    }
    assertThat(balance(tenantA, variantA)).isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payment_event WHERE status<>'PROCESSED'", Integer.class))
        .isZero();
  }

  @Test
  void eachNotificationIsResolvedToItsOwnStoreAndTokenNeverTheNotificationsClaims()
      throws Exception {
    Order a = paidLater("confirma-a", variantA, 1);
    Order b = paidLater("confirma-b", variantB, 1);
    approve(b);
    // Claims seller A in the body; the stored payment says B, so B's token reads B's order.
    var claimed =
        post(
            b.provider(),
            "{\"id\":\"evt-b\",\"type\":\"order\",\"action\":\"order.processed\",\"user_id\":111111,"
                + "\"data\":{\"id\":\""
                + b.provider()
                + "\"}}",
            "req-b",
            signature(b.provider(), "req-b"));
    assertThat(claimed.statusCode()).isEqualTo(200);
    events.processDue(20);
    assertThat(READS).containsExactly(TOKEN_B + " " + b.provider());
    assertThat(orderStatus(b)).isEqualTo("PAID");
    assertThat(orderStatus(a)).isEqualTo("PENDING_PAYMENT");
    assertThat(balance(tenantA, variantA)).isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT tenant_id FROM payment_event WHERE event_key LIKE '%:req-b'", UUID.class))
        .isEqualTo(tenantB);

    // Unknown provider order: recorded as IGNORED, never queried.
    assertThat(webhook("ORDUNKNOWN", "evt-u", "req-u").statusCode()).isEqualTo(200);
    assertThat(eventStatus("req-u")).isEqualTo("IGNORED");

    // Store A reconnected to another seller: that token must not read A's existing order.
    jdbc.update("UPDATE payment_account SET provider_user_id='333333' WHERE tenant_id=?", tenantA);
    approve(a);
    webhook(a.provider(), "evt-a", "req-a");
    events.processDue(20);
    assertThat(eventStatus("req-a")).isEqualTo("FAILED");
    assertThat(READS).hasSize(1);
    assertThat(orderStatus(a)).isEqualTo("PENDING_PAYMENT");
  }

  @Test
  void floodsOfBadSignaturesNeverBlockSignedDeliveriesAndIdsMatchWithoutCase() throws Exception {
    Order order = paidLater("confirma-a", variantA, 1);
    approve(order);
    String ts = String.valueOf(Instant.now().getEpochSecond());
    for (int i = 0; i < 320; i++) {
      int status =
          post(
                  order.provider(),
                  body(order.provider(), "x"),
                  "req-f" + i,
                  "ts=" + ts + ",v1=" + "0".repeat(64))
              .statusCode();
      assertThat(status).isIn(401, 429); // the ceiling itself: WebhookRateLimitTest
    }

    // A signed delivery still goes through, with the id in lowercase as the provider may send it.
    String lower = order.provider().toLowerCase();
    var signed = post(lower, body(lower, "evt-lower"), "req-lower", signature(lower, "req-lower"));
    assertThat(signed.statusCode()).isEqualTo(200);
    assertThat(eventStatus("req-lower")).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT provider_order_id FROM payment_event WHERE event_key LIKE '%:req-lower'",
                String.class))
        .isEqualTo(order.provider());
    events.processDue(20);
    assertThat(orderStatus(order)).isEqualTo("PAID");
  }

  @Test
  void providerAnswersWithoutReferenceOrTotalNeverConfirm() throws Exception {
    Order order = paidLater("confirma-a", variantA, 1);
    approve(order);
    ORDERS.get(order.provider()).remove("external_reference");
    webhook(order.provider(), "evt-n1", "req-n1");
    events.processDue(20);
    assertThat(eventStatus("req-n1")).isEqualTo("PENDING");
    ORDERS.get(order.provider()).put("external_reference", order.id().toString());
    ORDERS.get(order.provider()).remove("total_amount");
    webhook(order.provider(), "evt-n2", "req-n2");
    events.processDue(20);
    ORDERS.get(order.provider()).put("total_amount", "10.00");
    ORDERS.get(order.provider()).put("currency", "ARS");
    webhook(order.provider(), "evt-n3", "req-n3");
    events.processDue(20);
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    assertThat(jdbc.queryForList("SELECT last_error FROM payment_event", String.class))
        .hasSize(3)
        .containsOnly("PROVIDER_ERROR");
  }

  @Test
  void providerAnswersThatDoNotMatchTheStoredOrderNeverConfirm() throws Exception {
    Order order = paidLater("confirma-a", variantA, 1);
    approve(order);
    ORDERS.get(order.provider()).put("external_reference", UUID.randomUUID().toString());
    webhook(order.provider(), "evt-m1", "req-1");
    events.processDue(20);
    ORDERS.get(order.provider()).put("external_reference", order.id().toString());
    ORDERS.get(order.provider()).put("total_amount", "999.00");
    webhook(order.provider(), "evt-m2", "req-2");
    events.processDue(20);
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    // Transient-looking failures stay PENDING with a later attempt, never PROCESSED.
    assertThat(
            jdbc.queryForList(
                "SELECT status,attempts,last_error FROM payment_event ORDER BY received_at"))
        .allSatisfy(
            event -> {
              assertThat(event.get("status")).isEqualTo("PENDING");
              assertThat(event.get("attempts")).isEqualTo(1);
              assertThat(event.get("last_error")).isEqualTo("PROVIDER_ERROR");
            });
    assertThat(balance(tenantA, variantA)).isEqualTo(10);
  }

  @Test
  void reconciliationConfirmsWithoutAnyWebhookOnceWithEachStoresToken() throws Exception {
    Order a = paidLater("confirma-a", variantA, 2);
    Order b = paidLater("confirma-b", variantB, 1);
    Order unpaid = paidLater("confirma-a", variantA, 1);
    approve(a);
    approve(b);
    assertThat(reconciliation.reconcileDue(20)).isZero(); // first look only after a minute
    dueNow();
    assertThat(reconciliation.reconcileDue(20)).isEqualTo(3);
    assertThat(orderStatus(a)).isEqualTo("PAID");
    assertThat(orderStatus(b)).isEqualTo("PAID");
    assertThat(orderStatus(unpaid)).isEqualTo("PENDING_PAYMENT");
    assertThat(READS)
        .containsExactlyInAnyOrder(
            TOKEN_A + " " + a.provider(),
            TOKEN_B + " " + b.provider(),
            TOKEN_A + " " + unpaid.provider());
    assertThat(balance(tenantA, variantA)).isEqualTo(8);
    assertThat(balance(tenantB, variantB)).isEqualTo(9);
    // Approved payments leave the queue; the unpaid one comes back a minute later.
    assertThat(reconciliation.reconcileDue(20)).isZero();
    dueNow();
    READS.clear();
    assertThat(reconciliation.reconcileDue(20)).isEqualTo(1);
    assertThat(READS).containsExactly(TOKEN_A + " " + unpaid.provider());
    assertThat(sales(a)).hasSize(1);
  }

  @Test
  void concurrentReconciliationWebhookAndExpirationDeductExactlyOnce() throws Exception {
    List<Order> orders = new ArrayList<>();
    for (int i = 0; i < 4; i++) orders.add(paidLater("confirma-a", variantA, 1));
    for (Order order : orders) {
      approve(order);
      webhook(order.provider(), "evt-c" + order.provider(), "req-c" + order.provider());
    }
    dueNow();
    CyclicBarrier barrier = new CyclicBarrier(4);
    List<Callable<Integer>> work = new ArrayList<>();
    for (int i = 0; i < 2; i++)
      work.add(
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return reconciliation.reconcileDue(2);
          });
    work.add(
        () -> {
          barrier.await(10, TimeUnit.SECONDS);
          return events.processDue(4);
        });
    work.add(
        () -> {
          barrier.await(10, TimeUnit.SECONDS);
          return lifecycle.expireDue(10);
        });
    try (var executor = Executors.newFixedThreadPool(4)) {
      for (var result : executor.invokeAll(work)) result.get();
    }
    events.processDue(20);
    for (Order order : orders) {
      assertThat(orderStatus(order)).isEqualTo("PAID");
      assertThat(sales(order)).hasSize(1);
    }
    assertThat(balance(tenantA, variantA)).isEqualTo(6);
  }

  @Test
  void providerOutagesAreRecordedAsDivergenceRetriedLaterAndShownToTheOwner() throws Exception {
    Order order = paidLater("confirma-a", variantA, 1);
    approve(order);
    DOWN.set(true);
    dueNow();
    assertThat(reconciliation.reconcileDue(20)).isEqualTo(1);
    assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT status,last_check_error,check_attempts FROM payment WHERE order_id=?",
            order.id());
    assertThat(row.get("status")).isEqualTo("CREATED");
    assertThat(row.get("last_check_error")).isEqualTo("PROVIDER_ERROR");
    assertThat(row.get("check_attempts")).isEqualTo(1);
    assertThat(reconciliation.reconcileDue(20)).isZero(); // retried later, not in a hot loop

    AdminOrderView pending = admin.find(owner(tenantA), tenantA, order.id());
    assertThat(pending.payment().status()).isEqualTo("CREATED");
    assertThat(pending.payment().providerOrderId()).isEqualTo(order.provider());
    assertThat(pending.payment().lastCheckError()).isEqualTo("PROVIDER_ERROR");
    assertThat(pending.payment().amount()).isEqualTo("10.00");
    assertThat(
            admin
                .find(owner(tenantA), tenantA, paidLater("confirma-a", variantA, 1).id())
                .payment()
                .status())
        .isEqualTo("CREATED");

    DOWN.set(false);
    dueNow();
    reconciliation.reconcileDue(20);
    AdminOrderView paid = admin.find(owner(tenantA), tenantA, order.id());
    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(paid.payment().status()).isEqualTo("APPROVED");
    assertThat(paid.payment().paidAmount()).isEqualTo("10.00");
    assertThat(paid.payment().providerStatus()).isEqualTo("processed");
    assertThat(paid.payment().approvedAt()).isNotNull();
    assertThat(paid.toString()).doesNotContain(TOKEN_A);
  }

  @Test
  void latePaymentsAreReconciledForADayAfterExpiryThenLeftAlone() throws Exception {
    Order late = paidLater("confirma-a", variantA, 1);
    expire(late);
    jdbc.update(
        "UPDATE payment SET provider_expires_at=now()-interval '2 hours' WHERE order_id=?",
        late.id());
    Order abandoned = paidLater("confirma-a", variantA, 1);
    expire(abandoned);
    jdbc.update(
        "UPDATE payment SET provider_expires_at=now()-interval '25 hours' WHERE order_id=?",
        abandoned.id());
    approve(late);
    approve(abandoned);
    dueNow();
    assertThat(reconciliation.reconcileDue(20)).isEqualTo(1);
    assertThat(orderStatus(late)).isEqualTo("PAID"); // P4: reserved again and deducted
    assertThat(orderStatus(abandoned)).isEqualTo("EXPIRED");
    assertThat(READS).containsExactly(TOKEN_A + " " + late.provider());
  }

  @Test
  void refundIsAskedOnceConfirmedOnlyByTheProviderAndRestocksExactlyOnce() throws Exception {
    Order order = paid("confirma-a", variantA, 2);
    assertThat(balance(tenantA, variantA)).isEqualTo(8);
    UUID owner = owner(tenantA);

    var requested =
        refunds.refund(owner, tenantA, order.id(), CancelReason.of("Cliente desistiu"), true);
    assertThat(requested.status()).isEqualTo("PAID"); // asked, not yet confirmed
    assertThat(requested.payment().refund().status()).isEqualTo("REQUESTED");
    assertThat(requested.payment().refund().providerAcceptedAt()).isNotNull();
    // Double click: same refund, same idempotency key, nothing new.
    refunds.refund(owner, tenantA, order.id(), CancelReason.of("Cliente desistiu"), true);
    assertThat(REFUNDS).hasSize(2).containsOnly(TOKEN_A + " " + refundKey(order));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_refund", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT requested_by FROM payment_refund WHERE order_id=?", UUID.class, order.id()))
        .isEqualTo(owner);
    assertThat(balance(tenantA, variantA)).isEqualTo(8);

    // The provider confirms later; the reconciliation settles it once.
    ORDERS.get(order.provider()).put("status_detail", "refunded");
    dueNow();
    reconciliation.reconcileDue(20);
    webhook(order.provider(), "evt-ref", "req-ref");
    events.processDue(20);
    dueNow();
    reconciliation.reconcileDue(20);
    assertThat(orderStatus(order)).isEqualTo("REFUNDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM payment WHERE order_id=?", String.class, order.id()))
        .isEqualTo("REFUNDED");
    assertThat(balance(tenantA, variantA)).isEqualTo(10);
    assertThat(
            jdbc.queryForList(
                "SELECT delta,actor_kind,reason FROM inventory_movement WHERE order_id=?"
                    + " AND type='SALE_REVERSAL'",
                order.id()))
        .singleElement()
        .satisfies(
            reversal -> {
              assertThat(reversal.get("delta")).isEqualTo(2);
              assertThat(reversal.get("actor_kind")).isEqualTo("SYSTEM");
              assertThat(reversal.get("reason").toString()).startsWith("Devolução do pedido nº ");
            });
    var refunded = admin.find(owner, tenantA, order.id());
    assertThat(refunded.refundedAt()).isNotNull();
    assertThat(refunded.payment().refund().status()).isEqualTo("CONFIRMED");
    // A refunded order is returned as is: no new provider call.
    refunds.refund(owner, tenantA, order.id(), CancelReason.of("Outra vez"), false);
    assertThat(REFUNDS).hasSize(2);
    JsonNode buyer = JSON.readTree(get("confirma-a", order).body());
    assertThat(buyer.get("status").asString()).isEqualTo("REFUNDED");
  }

  @Test
  void refundWithoutRestockOrForAStocklessOrderNeverTouchesStock() throws Exception {
    REFUND_MODE.set("IMMEDIATE");
    Order kept = paid("confirma-a", variantA, 2);
    var view =
        refunds.refund(owner(tenantA), tenantA, kept.id(), CancelReason.of("Defeito"), false);
    assertThat(view.status()).isEqualTo("REFUNDED");
    assertThat(balance(tenantA, variantA)).isEqualTo(8);

    // Paid without stock (P4) and refunded with restock: nothing was deducted, nothing returns.
    Order stockless = paidLater("confirma-a", variantA, 2);
    expire(stockless);
    Order taker = paidLater("confirma-a", variantA, 8);
    approve(stockless);
    webhook(stockless.provider(), "evt-sl", "req-sl");
    events.processDue(20);
    assertThat(
            jdbc.queryForObject(
                "SELECT paid_without_stock FROM sales_order WHERE id=?",
                Boolean.class,
                stockless.id()))
        .isTrue();
    refunds.refund(owner(tenantA), tenantA, stockless.id(), CancelReason.of("Sem estoque"), true);
    assertThat(orderStatus(stockless)).isEqualTo("REFUNDED");
    assertThat(balance(tenantA, variantA)).isEqualTo(8);
    assertThat(reservationStatuses(taker)).containsOnly("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movement WHERE type='SALE_REVERSAL'",
                Integer.class))
        .isZero();
  }

  @Test
  void refundGuardsConflictsProviderFailuresAndOtherStores() throws Exception {
    Order paidA = paid("confirma-a", variantA, 1);
    Order pending = paidLater("confirma-a", variantA, 1);
    UUID ownerA = owner(tenantA);
    UUID ownerB = owner(tenantB);
    assertThat(refundFails(ownerA, tenantA, pending, true))
        .isEqualTo(OrderException.Reason.NOT_REFUNDABLE);
    assertThat(refundFails(ownerB, tenantA, paidA, true)).isEqualTo(OrderException.Reason.MISSING);
    assertThat(refundFails(ownerB, tenantB, paidA, true)).isEqualTo(OrderException.Reason.MISSING);
    assertThat(REFUNDS).isEmpty();

    REFUND_MODE.set("FAIL");
    assertThat(refundFails(ownerA, tenantA, paidA, true))
        .isEqualTo(OrderException.Reason.PAYMENT_FAILED);
    assertThat(admin.find(ownerA, tenantA, paidA.id()).payment().refund().lastError())
        .isEqualTo("PROVIDER_ERROR");
    // The other stock choice for the same refund is a conflict, never a second refund.
    assertThat(refundFails(ownerA, tenantA, paidA, false))
        .isEqualTo(OrderException.Reason.REFUND_CONFLICT);

    // Retried: the provider says it was already refunded; read back and settled once.
    REFUND_MODE.set("ALREADY");
    var view = refunds.refund(ownerA, tenantA, paidA.id(), CancelReason.of("Tentativa"), true);
    assertThat(view.status()).isEqualTo("REFUNDED");
    assertThat(view.payment().refund().lastError()).isNull();
    assertThat(REFUNDS).containsOnly(TOKEN_A + " " + refundKey(paidA));
    assertThat(balance(tenantA, variantA)).isEqualTo(10); // the paid unit came back to stock
  }

  // --- helpers -----------------------------------------------------------------------------

  record Order(UUID id, String token, String provider) {}

  /** A pending order with its Checkout Pro order created at the fake provider. */
  private Order paidLater(String slug, UUID variant, int quantity) throws Exception {
    String total = String.format("%d.00", quantity * (slug.endsWith("a") ? 10 : 5));
    var created = postOrder(slug, variant, quantity, total);
    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode result = JSON.readTree(created.body());
    UUID id = UUID.fromString(result.get("order").get("id").asString());
    String token = result.get("accessToken").asString();
    var paid =
        client.send(
            HttpRequest.newBuilder(uri("/api/public/stores/" + slug + "/orders/" + id + "/payment"))
                .timeout(Duration.ofSeconds(30))
                .header("X-Order-Token", token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(paid.statusCode()).isEqualTo(200);
    String provider =
        jdbc.queryForObject(
            "SELECT provider_order_id FROM payment WHERE order_id=?", String.class, id);
    return new Order(id, token, provider);
  }

  /** The buyer paid in full at the provider. */
  private static void approve(Order order) {
    Map<String, Object> state = ORDERS.get(order.provider());
    state.put("status", "processed");
    state.put("status_detail", "accredited");
    state.put("total_paid_amount", state.get("total_amount"));
  }

  private void expire(Order order) {
    jdbc.update(
        "UPDATE sales_order SET created_at=now()-interval '2 hours',"
            + " reservation_expires_at=now()-interval '1 minute' WHERE id=?",
        order.id());
    jdbc.update(
        "UPDATE inventory_reservation SET created_at=now()-interval '2 hours',"
            + " expires_at=now()-interval '1 minute' WHERE order_id=?",
        order.id());
    assertThat(lifecycle.expireDue(10)).isEqualTo(1);
  }

  private void cancel(Order order) {
    jdbc.update(
        "UPDATE sales_order SET status='CANCELLED', cancelled_at=now(),"
            + " cancelled_by=(SELECT user_id FROM tenant_membership WHERE tenant_id=sales_order.tenant_id),"
            + " cancel_reason='Teste sintético' WHERE id=?",
        order.id());
    jdbc.update(
        "UPDATE inventory_reservation SET status='RELEASED', closed_at=now() WHERE order_id=?",
        order.id());
  }

  private HttpResponse<String> webhook(String provider, String eventId, String requestId)
      throws Exception {
    return post(provider, body(provider, eventId), requestId, signature(provider, requestId));
  }

  private static String body(String provider, String eventId) {
    return "{\"id\":\""
        + eventId
        + "\",\"type\":\"order\",\"action\":\"order.processed\",\"api_version\":\"v1\","
        + "\"live_mode\":false,\"data\":{\"id\":\""
        + provider
        + "\",\"status\":\"processed\"}}";
  }

  private static String signature(String provider, String requestId) {
    String ts = String.valueOf(Instant.now().toEpochMilli());
    return "ts="
        + ts
        + ",v1="
        + WebhookSignatureTest.sign(WEBHOOK_SECRET, manifest(provider, requestId, ts));
  }

  private static String stale(String provider, String requestId, Instant at) {
    String ts = String.valueOf(at.getEpochSecond());
    return "ts="
        + ts
        + ",v1="
        + WebhookSignatureTest.sign(WEBHOOK_SECRET, manifest(provider, requestId, ts));
  }

  private static String manifest(String provider, String requestId, String ts) {
    return "id:" + provider + ";request-id:" + requestId + ";ts:" + ts + ";";
  }

  private HttpResponse<String> post(String dataId, String body, String requestId, String signature)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                uri("/api/public/webhooks/mercado-pago?data.id=" + dataId + "&type=order"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("x-request-id", requestId)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (signature != null) request.header("x-signature", signature);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postOrder(String slug, UUID variant, int quantity, String total)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", UUID.randomUUID().toString());
    body.put("items", List.of(Map.of("variantId", variant.toString(), "quantity", quantity)));
    body.put("fulfillment", Map.of("type", "PICKUP"));
    body.put(
        "buyer",
        Map.of(
            "name",
            "Comprador Sintético",
            "email",
            "comprador-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test",
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

  private HttpResponse<String> get(String slug, Order order) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri("/api/public/stores/" + slug + "/orders/" + order.id()))
            .timeout(Duration.ofSeconds(20))
            .header("X-Order-Token", order.token())
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** The admin read model's paid fields, read straight from the table the admin view uses. */
  /** A PAID order: created, paid at the provider and confirmed through the webhook queue. */
  private Order paid(String slug, UUID variant, int quantity) throws Exception {
    Order order = paidLater(slug, variant, quantity);
    approve(order);
    webhook(order.provider(), "evt-paid-" + order.provider(), "req-paid-" + order.provider());
    events.processDue(20);
    assertThat(orderStatus(order)).isEqualTo("PAID");
    return order;
  }

  private OrderException.Reason refundFails(UUID actor, UUID tenant, Order order, boolean restock) {
    try {
      refunds.refund(actor, tenant, order.id(), CancelReason.of("Motivo sintético"), restock);
    } catch (OrderException failure) {
      return failure.reason();
    }
    throw new AssertionError("Refund should have failed");
  }

  private static String refundKey(Order order) {
    return UUID.nameUUIDFromBytes(("sc-refund:" + order.id()).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  /** Makes every waiting payment due for the reconciliation routine now. */
  private void dueNow() {
    jdbc.update(
        "UPDATE payment SET next_check_at=now()-interval '1 second' WHERE status='CREATED'");
  }

  private UUID owner(UUID tenant) {
    return jdbc.queryForObject(
        "SELECT user_id FROM tenant_membership WHERE tenant_id=?", UUID.class, tenant);
  }

  private String adminDetailJson(Order order) throws Exception {
    return JSON.writeValueAsString(
        jdbc.queryForMap(
            "SELECT status,paid_at IS NOT NULL AS paid,paid_without_stock FROM sales_order WHERE id=?",
            order.id()));
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private String orderStatus(Order order) {
    return jdbc.queryForObject(
        "SELECT status FROM sales_order WHERE id=?", String.class, order.id());
  }

  /** Status of the event recorded for the signed request {@code requestId}. */
  private String eventStatus(String requestId) {
    return jdbc.queryForObject(
        "SELECT status FROM payment_event WHERE event_key LIKE ?", String.class, "%:" + requestId);
  }

  private int eventCount() {
    return jdbc.queryForObject("SELECT count(*) FROM payment_event", Integer.class);
  }

  private int balance(UUID tenant, UUID variant) {
    return jdbc.queryForObject(
        "SELECT quantity FROM inventory_balance WHERE tenant_id=? AND product_id=?",
        Integer.class,
        tenant,
        variant);
  }

  private List<Map<String, Object>> sales(Order order) {
    return jdbc.queryForList(
        "SELECT delta,balance_after,actor_kind,actor_id,reason FROM inventory_movement"
            + " WHERE order_id=? AND type='SALE'",
        order.id());
  }

  private List<String> reservationStatuses(Order order) {
    return jdbc.queryForList(
        "SELECT status FROM inventory_reservation WHERE order_id=?", String.class, order.id());
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
        SELECT id,tenant_id,true,'Retirada sintética',false,NULL,NULL,now()
        FROM store WHERE tenant_id=?
        """,
        tenant);
    return tenant;
  }

  private UUID variant(UUID tenant, String price, int stock) {
    UUID product = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO catalog_product(id,tenant_id,store_id,name,sku,price,currency,created_at,updated_at,published,kind) SELECT ?,?,id,'Produto',?,?::numeric,'BRL',now(),now(),true,'SIMPLE' FROM store WHERE tenant_id=?",
        product,
        tenant,
        "SKU-" + product.toString().substring(0, 8),
        price,
        tenant);
    jdbc.update(
        "INSERT INTO catalog_variant(id,tenant_id,product_id,sku,price,cost,currency,options_json,combination_key) VALUES (?,?,?,?,?::numeric,NULL,'BRL','[]',?)",
        product,
        tenant,
        product,
        "SKU-" + product.toString().substring(0, 8),
        price,
        product.toString());
    jdbc.update(
        "INSERT INTO inventory_balance(product_id,tenant_id,quantity) VALUES(?,?,?)",
        product,
        tenant,
        stock);
    return product;
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
