package com.smartcommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.provider.MercadoPagoOrdersClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Checkout Pro failures are diagnosable from the log (HTTP status, provider error codes, which
 * response check failed) without the token, the response body or any request value.
 */
@ExtendWith(OutputCaptureExtension.class)
class OrdersClientDiagnosticsTest {
  private static final String TOKEN = "APP_USR-synthetic-seller-token";
  private final AtomicReference<Integer> status = new AtomicReference<>(400);
  private final AtomicReference<String> body = new AtomicReference<>("{}");
  private HttpServer server;

  private MercadoPagoOrdersClient client() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/orders",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status.get(), bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return new MercadoPagoOrdersClient(
        "http://127.0.0.1:" + server.getAddress().getPort(), "https://loja.example.test");
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  private static CheckoutPaymentRequest request() {
    return new CheckoutPaymentRequest(
        UUID.randomUUID(),
        "loja-teste",
        UUID.randomUUID(),
        new BigDecimal("49.90"),
        List.of(new CheckoutPaymentRequest.Line("Camiseta Teste", new BigDecimal("49.90"), 1)));
  }

  @Test
  void rejectedOrderLogsStatusAndProviderCodesOnly(CapturedOutput output) throws Exception {
    var client = client();
    body.set(
        "{\"errors\":[{\"code\":\"invalid_back_urls\",\"message\":\"buyer comprador@example.test\"},"
            + "{\"code\":\"<script>\",\"message\":\"x\"}],\"error\":\"bad_request\"}");
    assertThatThrownBy(() -> client.createCheckoutOrder(TOKEN, "key-1", request(), Instant.now()))
        .isInstanceOf(PaymentException.class);
    assertThat(output.getAll())
        .contains(
            "Checkout Pro order rejected: HTTP 400, provider codes [bad_request, invalid_back_urls]")
        .doesNotContain(TOKEN, "comprador@example.test", "<script>", "loja-teste");
  }

  @Test
  void acceptedStatusWithUnexpectedResponseNamesTheFailedCheck(CapturedOutput output)
      throws Exception {
    var client = client();
    status.set(201);
    body.set(
        "{\"id\":\"ORD01\",\"status\":\"created\",\"total_amount\":\"49.90\","
            + "\"checkout_url\":\"https://evil.example.test/pay?token=abc\"}");
    assertThatThrownBy(() -> client.createCheckoutOrder(TOKEN, "key-2", request(), Instant.now()))
        .isInstanceOf(PaymentException.class);
    body.set("{\"id\":\"ORD01\",\"status\":\"created\",\"total_amount\":\"49.90\"}");
    assertThatThrownBy(() -> client.createCheckoutOrder(TOKEN, "key-3", request(), Instant.now()))
        .isInstanceOf(PaymentException.class);
    body.set("not json");
    assertThatThrownBy(() -> client.createCheckoutOrder(TOKEN, "key-4", request(), Instant.now()))
        .isInstanceOf(PaymentException.class);
    assertThat(output.getAll())
        .contains("Checkout Pro order response not accepted: checkout_url host evil.example.test")
        .contains("Checkout Pro order response not accepted: checkout_url missing")
        .contains("Checkout Pro order response not accepted: body is not a JSON object")
        .doesNotContain(TOKEN, "token=abc");
  }
}
