package com.smartcommerce.payments.provider;

import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.ProviderOrder;
import com.smartcommerce.payments.domain.ProviderOrderStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checkout Pro through the Orders API (ADR-0013 P1, P2): {@code POST /v1/orders} with the seller's
 * token and an idempotency key. The order expires with the reservation ({@link #EXPIRATION}) and
 * only accepts card, Pix and Mercado Pago balance. The answer is checked against the request
 * (amount, currency, reference) and the checkout URL must be an HTTPS Mercado Pago page, so a
 * tampered or confused response never redirects the buyer elsewhere. Tokens never reach logs or
 * exception messages, and redirects are never followed.
 */
public final class MercadoPagoOrdersClient {
  private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoOrdersClient.class);
  public static final String DEFAULT_API_URL = "https://api.mercadopago.com";

  /** P2: same window as the reservation (ADR-0012 D1); the Orders API minimum is 30 minutes. */
  static final Duration EXPIRATION = Duration.ofMinutes(30);

  /**
   * P2: boleto ({@code ticket}), cash points ({@code atm}) and Mercado Crédito ({@code
   * digital_currency}) settle after the reservation or are out of scope; card, Pix and balance
   * remain. To be confirmed against {@code GET /v1/payment_methods} in the sandbox.
   */
  static final List<String> NOT_ALLOWED_TYPES = List.of("ticket", "atm", "digital_currency");

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_TITLE = 250;
  private final URI ordersUrl;
  private final URI returnOrigin;
  private final HttpClient http;

  /**
   * @param returnOrigin public HTTPS origin of the storefront for the return pages; empty or
   *     non-HTTPS (local development) sends no return URLs, since the provider rejects local ones.
   */
  public MercadoPagoOrdersClient(String apiUrl, String returnOrigin) {
    URI api = MercadoPagoOAuthClient.endpoint(apiUrl, DEFAULT_API_URL, "api-url");
    if (!api.getPath().isEmpty() && !"/".equals(api.getPath()))
      throw new IllegalStateException("payments.mercado-pago.api-url must be an origin");
    this.ordersUrl = api.resolve("/v1/orders");
    this.returnOrigin = origin(returnOrigin);
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public ProviderOrder createCheckoutOrder(
      String accessToken, String idempotencyKey, CheckoutPaymentRequest request, Instant now) {
    HttpResponse<String> response;
    try {
      response =
          http.send(
              HttpRequest.newBuilder(ordersUrl)
                  .timeout(TIMEOUT)
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json")
                  .header("Authorization", "Bearer " + accessToken)
                  .header("X-Idempotency-Key", idempotencyKey)
                  .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body(request))))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    } catch (IOException failure) {
      LOG.warn("Checkout Pro order request failed: {}", failure.getClass().getSimpleName());
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    if (response.statusCode() != 200 && response.statusCode() != 201) {
      LOG.warn(
          "Checkout Pro order rejected: HTTP {}, provider codes {}",
          response.statusCode(),
          errorCodes(parse(response.body())));
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    return order(parse(response.body()), request, now);
  }

  /**
   * Diagnostic codes from a provider error body ({@code code}, {@code blocked_by}, {@code error},
   * {@code errors[].code}, {@code cause[].code}; a policy denial answers 403 with {@code code} and
   * {@code blocked_by}): only short identifier-like values, at most five, never messages or the
   * body, which may echo request data.
   */
  static List<String> errorCodes(JsonNode json) {
    List<String> codes = new ArrayList<>();
    if (json == null) return codes;
    Consumer<JsonNode> add =
        node -> {
          if (node != null && (node.isString() || node.isNumber())) {
            String value = node.asString();
            if (value.matches("[A-Za-z0-9_.-]{1,60}") && codes.size() < 5) codes.add(value);
          }
        };
    add.accept(json.get("code"));
    add.accept(json.get("blocked_by"));
    add.accept(json.get("error"));
    for (String list : List.of("errors", "cause")) {
      JsonNode items = json.get(list);
      if (items != null && items.isArray())
        for (JsonNode item : items) add.accept(item.get("code"));
    }
    return codes;
  }

  /**
   * Reads a provider order with the seller's token (SC-014, ADR-0013 P5): the only way a payment is
   * confirmed. {@code id} must be a stored provider id; it is validated again before it becomes
   * part of the path.
   */
  public ProviderOrderStatus fetchOrder(String accessToken, String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}"))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    HttpResponse<String> response;
    try {
      response =
          http.send(
              HttpRequest.newBuilder(ordersUrl.resolve("/v1/orders/" + id))
                  .timeout(TIMEOUT)
                  .header("Accept", "application/json")
                  .header("Authorization", "Bearer " + accessToken)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    } catch (IOException failure) {
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    if (response.statusCode() != 200)
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    JsonNode json = parse(response.body());
    if (json == null) throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    String status = text(json, "status", 40);
    String detail = text(json, "status_detail", 60);
    if (!id.equals(text(json, "id", 64))
        || status == null
        || !status.matches("[a-z_]{1,40}")
        || (detail != null && !detail.matches("[a-z_]{1,60}")))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    JsonNode user = json.get("user_id");
    String userId = user == null || user.isNull() ? null : user.asString();
    if (userId != null && !userId.matches("[0-9]{1,40}"))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    return new ProviderOrderStatus(
        id,
        status,
        detail,
        amount(text(json, "total_amount", 40)),
        amount(text(json, "total_paid_amount", 40)),
        text(json, "external_reference", 64),
        userId,
        text(json, "currency", 3));
  }

  /**
   * Asks for the full refund of a provider order (SC-016, ADR-0013 P9): no amount in the body means
   * the whole order. The idempotency key is the same on every attempt, so a retry or a double click
   * never refunds twice; answers saying the order is already refunded, or that a refund is in
   * process, are an accepted request too. The refund is confirmed later by reading the order, never
   * by this answer alone.
   */
  public void refundOrder(String accessToken, String id, String idempotencyKey) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}"))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    HttpResponse<String> response;
    try {
      response =
          http.send(
              HttpRequest.newBuilder(ordersUrl.resolve("/v1/orders/" + id + "/refund"))
                  .timeout(TIMEOUT)
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json")
                  .header("Authorization", "Bearer " + accessToken)
                  .header("X-Idempotency-Key", idempotencyKey)
                  .POST(HttpRequest.BodyPublishers.ofString("{}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    } catch (IOException failure) {
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    if (response.statusCode() == 200 || response.statusCode() == 201) return;
    JsonNode json = parse(response.body());
    String code = json == null ? null : text(json, "code", 60);
    if (code == null && json != null) code = text(json, "error", 60);
    if (response.statusCode() == 409 && ALREADY_REFUNDING.contains(code)) return;
    throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
  }

  /**
   * 409 answers that mean the refund was already done or is in process (Orders API reference).
   * {@code cannot_refund_order} and {@code idempotency_key_already_used} are failures: the refund
   * may not exist, so the store sees an error and may retry (SC-017 review).
   */
  static final List<String> ALREADY_REFUNDING =
      List.of("order_already_refunded", "order_refund_already_in_process");

  private static BigDecimal amount(String value) {
    if (value == null) return null;
    if (!value.matches("[0-9]{1,18}(\\.[0-9]{1,2})?"))
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    return new BigDecimal(value);
  }

  Map<String, Object> body(CheckoutPaymentRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "online");
    body.put("processing_mode", "manual");
    body.put("total_amount", money(request.total()));
    body.put("external_reference", request.orderId().toString());
    body.put("expiration_time", EXPIRATION.toString());
    body.put(
        "items",
        request.lines().stream()
            .map(
                line -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("title", title(line.title()));
                  item.put("unit_price", money(line.unitPrice()));
                  item.put("quantity", line.quantity());
                  return item;
                })
            .toList());
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("payment_method", Map.of("not_allowed_types", NOT_ALLOWED_TYPES));
    if (returnOrigin != null) {
      // One return page for every outcome: it never trusts the query string (SC-013).
      String back =
          returnOrigin
              .resolve(
                  "/lojas/" + request.storeSlug() + "/pedidos/" + request.orderId() + "/pagamento")
              .toString();
      Map<String, Object> online = new LinkedHashMap<>();
      online.put("success_url", back);
      online.put("failure_url", back);
      online.put("pending_url", back);
      online.put("auto_return", "approved");
      config.put("online", online);
    }
    body.put("config", config);
    return body;
  }

  private static ProviderOrder order(JsonNode json, CheckoutPaymentRequest request, Instant now) {
    if (json == null) {
      LOG.warn("Checkout Pro order response not accepted: body is not a JSON object");
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    String id = text(json, "id", 64);
    String status = text(json, "status", 40);
    String url = text(json, "checkout_url", 2048);
    String total = text(json, "total_amount", 40);
    String currency = text(json, "currency", 3);
    String reference = text(json, "external_reference", 64);
    String invalid =
        id == null || !id.matches("[A-Za-z0-9_-]{1,64}")
            ? "id"
            : status == null || !status.matches("[a-z_]{1,40}")
                ? "status"
                : !checkoutPage(url)
                    ? url == null ? "checkout_url missing" : "checkout_url host " + host(url)
                    : !sameAmount(total, request.total())
                        ? "total_amount"
                        : currency != null && !"BRL".equals(currency)
                            ? "currency"
                            : reference != null && !reference.equals(request.orderId().toString())
                                ? "external_reference"
                                : null;
    if (invalid != null) {
      // Field names only (and the checkout host, never the full URL or any value).
      LOG.warn("Checkout Pro order response not accepted: {}", invalid);
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    return new ProviderOrder(id, status, url, now.plus(EXPIRATION));
  }

  private static String host(String value) {
    try {
      String host = URI.create(value).getHost();
      return host == null ? "none" : host.replaceAll("[^A-Za-z0-9.-]", "").toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException invalid) {
      return "invalid";
    }
  }

  /** HTTPS on a Mercado Pago domain (the domain varies by the seller's country). */
  static boolean checkoutPage(String value) {
    if (value == null) return false;
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException invalid) {
      return false;
    }
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    return "https".equals(uri.getScheme())
        && uri.getUserInfo() == null
        && uri.getPort() == -1
        && uri.getFragment() == null
        && host.matches("([a-z0-9-]+\\.)*mercadopago\\.com(\\.[a-z]{2})?");
  }

  private static boolean sameAmount(String value, BigDecimal expected) {
    if (value == null || !value.matches("[0-9]{1,18}(\\.[0-9]{1,2})?")) return false;
    return new BigDecimal(value).compareTo(expected) == 0;
  }

  private static String title(String value) {
    String clean = value.replaceAll("[\\p{Cntrl}]", " ").strip();
    return clean.length() <= MAX_TITLE ? clean : clean.substring(0, MAX_TITLE);
  }

  private static String money(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static String text(JsonNode json, String field, int max) {
    JsonNode node = json.get(field);
    if (node == null || !node.isString()) return null;
    String value = node.asString();
    return value.isBlank() || value.length() > max ? null : value;
  }

  private static JsonNode parse(String body) {
    if (body == null || body.length() > 65_536) return null;
    try {
      JsonNode node = JSON.readTree(body);
      return node != null && node.isObject() ? node : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private static URI origin(String configured) {
    if (configured == null || configured.isBlank()) return null;
    URI uri;
    try {
      uri = URI.create(configured.strip());
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid payments.mercado-pago.return-origin");
    }
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
        || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
      throw new IllegalStateException(
          "payments.mercado-pago.return-origin must be the storefront origin");
    return "https".equals(uri.getScheme()) ? uri : null;
  }
}
