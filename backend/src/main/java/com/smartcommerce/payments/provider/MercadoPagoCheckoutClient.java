package com.smartcommerce.payments.provider;

import com.smartcommerce.payments.domain.CheckoutPaymentRequest;
import com.smartcommerce.payments.domain.PaymentException;
import com.smartcommerce.payments.domain.ProviderOrder;
import com.smartcommerce.payments.domain.ProviderPayment;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checkout Pro through preferences (ADR-0013, change of P1): {@code POST /checkout/preferences}
 * with the seller's token, then the payments API to read ({@code GET /v1/payments/{id}}, {@code GET
 * /v1/payments/search?external_reference=}) and refund ({@code POST /v1/payments/{id}/refunds}) the
 * buyer's payment.
 *
 * <p>The preference expires with the reservation ({@link #EXPIRATION}) and excludes boleto, cash
 * points and Mercado Crédito (P2). Answers are checked against the request and the checkout URL
 * must be an HTTPS Mercado Pago page, so a tampered or confused response never redirects the buyer
 * elsewhere. Tokens never reach logs or exception messages, failures are logged with codes and
 * field names only, and redirects are never followed.
 */
public final class MercadoPagoCheckoutClient {
  public static final String DEFAULT_API_URL = "https://api.mercadopago.com";
  private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoCheckoutClient.class);

  /** P2: same window as the reservation (ADR-0012 D1). */
  static final Duration EXPIRATION = Duration.ofMinutes(30);

  /**
   * P2: boleto, cash points and Mercado Crédito settle after the reservation or are out of scope.
   */
  static final List<String> EXCLUDED_TYPES = List.of("ticket", "atm", "digital_currency");

  private static final DateTimeFormatter PROVIDER_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
  private static final ZoneOffset SAO_PAULO = ZoneOffset.ofHours(-3);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_TITLE = 250;
  private final URI api;
  private final URI returnOrigin;
  private final URI notificationUrl;
  private final HttpClient http;

  /**
   * @param returnOrigin public HTTPS origin of the storefront for the return page; empty or
   *     non-HTTPS (local development) sends no return URLs, since the provider rejects local ones.
   * @param notificationUrl public HTTPS webhook URL sent with each preference (multi-seller
   *     platforms); empty relies on the application's webhook configuration.
   */
  public MercadoPagoCheckoutClient(String apiUrl, String returnOrigin, String notificationUrl) {
    URI configured = MercadoPagoOAuthClient.endpoint(apiUrl, DEFAULT_API_URL, "api-url");
    if (!configured.getPath().isEmpty() && !"/".equals(configured.getPath()))
      throw new IllegalStateException("payments.mercado-pago.api-url must be an origin");
    this.api = configured;
    this.returnOrigin = origin(returnOrigin);
    this.notificationUrl = notification(notificationUrl);
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /** Creates the preference the buyer pays; its id takes the place of the provider order id. */
  public ProviderOrder createPreference(
      String accessToken, String idempotencyKey, CheckoutPaymentRequest request, Instant now) {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(api.resolve("/checkout/preferences"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body(request, now)))),
            "preference");
    if (response.statusCode() != 200 && response.statusCode() != 201) {
      LOG.warn(
          "Checkout Pro preference rejected: HTTP {}, provider codes {}",
          response.statusCode(),
          errorCodes(parse(response.body())));
      throw providerError();
    }
    JsonNode json = parse(response.body());
    String invalid = preferenceProblem(json, request);
    if (invalid != null) {
      // Field names only (and the checkout host, never the full URL or any value).
      LOG.warn("Checkout Pro preference response not accepted: {}", invalid);
      throw providerError();
    }
    return new ProviderOrder(
        text(json, "id", 64), "created", text(json, "init_point", 2048), now.plus(EXPIRATION));
  }

  /**
   * Reads one payment with the seller's token (P5): the only way a payment is confirmed. {@code id}
   * comes from a signed notification and is validated again before it becomes part of the path.
   */
  public ProviderPayment fetchPayment(String accessToken, String id) {
    if (id == null || !id.matches("[0-9]{1,40}")) throw providerError();
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(api.resolve("/v1/payments/" + id))
                .header("Authorization", "Bearer " + accessToken)
                .GET(),
            "payment");
    if (response.statusCode() != 200) {
      LOG.warn("Payment read rejected: HTTP {}", response.statusCode());
      throw providerError();
    }
    ProviderPayment payment = payment(parse(response.body()));
    if (payment == null || !id.equals(payment.id())) throw providerError();
    return payment;
  }

  /**
   * The buyer's payment of {@code order} (reconciliation), most relevant first: a full refund, an
   * approval, then the most recent attempt. Empty when the buyer has not paid yet.
   */
  public Optional<ProviderPayment> findPayment(String accessToken, String order) {
    if (order == null || !order.matches("[0-9a-f-]{36}")) throw providerError();
    String query =
        "/v1/payments/search?sort=date_created&criteria=desc&limit=20&external_reference="
            + URLEncoder.encode(order, StandardCharsets.UTF_8);
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(api.resolve(query))
                .header("Authorization", "Bearer " + accessToken)
                .GET(),
            "payment search");
    if (response.statusCode() != 200) {
      LOG.warn("Payment search rejected: HTTP {}", response.statusCode());
      throw providerError();
    }
    JsonNode json = parse(response.body());
    JsonNode results = json == null ? null : json.get("results");
    if (results == null || !results.isArray()) throw providerError();
    List<ProviderPayment> found = new ArrayList<>();
    for (JsonNode item : results) {
      ProviderPayment candidate = payment(item);
      if (candidate != null && order.equals(candidate.externalReference())) found.add(candidate);
    }
    return found.stream()
        .filter(ProviderPayment::refunded)
        .findFirst()
        .or(() -> found.stream().filter(p -> "approved".equals(p.status())).findFirst())
        .or(() -> found.stream().findFirst());
  }

  /**
   * Requests the full refund of a payment (P9). The same key on every attempt makes a retry the
   * same refund; whether it is done is always read back from the payment, never from this answer.
   */
  public void refundPayment(String accessToken, String id, String idempotencyKey) {
    if (id == null || !id.matches("[0-9]{1,40}")) throw providerError();
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(api.resolve("/v1/payments/" + id + "/refunds"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString("{}")),
            "refund");
    if (response.statusCode() == 200 || response.statusCode() == 201) return;
    LOG.warn(
        "Refund rejected: HTTP {}, provider codes {}",
        response.statusCode(),
        errorCodes(parse(response.body())));
    throw providerError();
  }

  Map<String, Object> body(CheckoutPaymentRequest request, Instant now) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put(
        "items",
        request.lines().stream()
            .map(
                line -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("title", title(line.title()));
                  item.put("quantity", line.quantity());
                  item.put("unit_price", amount(line.unitPrice()));
                  item.put("currency_id", "BRL");
                  return item;
                })
            .toList());
    body.put("external_reference", request.orderId().toString());
    body.put("expires", true);
    body.put("expiration_date_from", PROVIDER_TIME.format(now.atOffset(SAO_PAULO)));
    body.put("expiration_date_to", PROVIDER_TIME.format(now.plus(EXPIRATION).atOffset(SAO_PAULO)));
    body.put(
        "payment_methods",
        Map.of(
            "excluded_payment_types", EXCLUDED_TYPES.stream().map(t -> Map.of("id", t)).toList()));
    if (returnOrigin != null) {
      // One return page for every outcome: it never trusts the query string (SC-013).
      String back =
          returnOrigin
              .resolve(
                  "/lojas/" + request.storeSlug() + "/pedidos/" + request.orderId() + "/pagamento")
              .toString();
      body.put("back_urls", Map.of("success", back, "failure", back, "pending", back));
      body.put("auto_return", "approved");
    }
    if (notificationUrl != null) body.put("notification_url", notificationUrl.toString());
    return body;
  }

  private static String preferenceProblem(JsonNode json, CheckoutPaymentRequest request) {
    if (json == null) return "body is not a JSON object";
    String id = text(json, "id", 64);
    String url = text(json, "init_point", 2048);
    String reference = text(json, "external_reference", 64);
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) return "id";
    if (!checkoutPage(url))
      return url == null ? "init_point missing" : "init_point host " + host(url);
    if (reference != null && !reference.equals(request.orderId().toString()))
      return "external_reference";
    return null;
  }

  private static ProviderPayment payment(JsonNode json) {
    if (json == null || !json.isObject()) return null;
    String id = scalar(json, "id");
    String status = text(json, "status", 40);
    if (id == null
        || !id.matches("[0-9]{1,40}")
        || status == null
        || !status.matches("[a-z_]{1,40}")) return null;
    String detail = text(json, "status_detail", 60);
    String collector = json.get("collector_id") == null ? null : scalar(json, "collector_id");
    String currency = text(json, "currency_id", 3);
    return new ProviderPayment(
        id,
        status,
        detail != null && detail.matches("[a-z_]{1,60}") ? detail : null,
        decimal(json.get("transaction_amount")),
        decimal(json.get("transaction_amount_refunded")),
        text(json, "external_reference", 64),
        collector != null && collector.matches("[0-9]{1,40}") ? collector : null,
        currency);
  }

  private HttpResponse<String> send(HttpRequest.Builder request, String what) {
    try {
      return http.send(
          request.timeout(TIMEOUT).header("Accept", "application/json").build(),
          HttpResponse.BodyHandlers.ofString());
    } catch (IOException failure) {
      LOG.warn("Mercado Pago {} request failed: {}", what, failure.getClass().getSimpleName());
      throw providerError();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw providerError();
    }
  }

  /**
   * Diagnostic codes from a provider error body ({@code code}, {@code blocked_by}, {@code error},
   * {@code errors[].code}, {@code cause[].code}): only short identifier-like values, at most five,
   * never messages or the body, which may echo request data.
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

  private static String host(String value) {
    try {
      String host = URI.create(value).getHost();
      return host == null ? "none" : host.replaceAll("[^A-Za-z0-9.-]", "").toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException invalid) {
      return "invalid";
    }
  }

  private static BigDecimal decimal(JsonNode node) {
    if (node == null || !node.isNumber()) return null;
    BigDecimal value = node.decimalValue();
    return value.signum() < 0 || value.scale() > 2 && value.stripTrailingZeros().scale() > 2
        ? null
        : value.setScale(2, RoundingMode.UNNECESSARY);
  }

  private static BigDecimal amount(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }

  private static String title(String value) {
    String clean = value.replaceAll("[\\p{Cntrl}]", " ").strip();
    return clean.length() <= MAX_TITLE ? clean : clean.substring(0, MAX_TITLE);
  }

  private static String text(JsonNode json, String field, int max) {
    JsonNode node = json.get(field);
    if (node == null || !node.isString()) return null;
    String value = node.asString();
    return value.isBlank() || value.length() > max ? null : value;
  }

  private static String scalar(JsonNode json, String field) {
    JsonNode node = json.get(field);
    if (node == null || !(node.isString() || node.isIntegralNumber())) return null;
    String value = node.asString();
    return value.length() > 40 ? null : value;
  }

  private static JsonNode parse(String body) {
    if (body == null || body.length() > 262_144) return null;
    try {
      JsonNode node = JSON.readTree(body);
      return node != null && node.isObject() ? node : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private static PaymentException providerError() {
    return new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
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

  private static URI notification(String configured) {
    if (configured == null || configured.isBlank()) return null;
    URI uri;
    try {
      uri = URI.create(configured.strip());
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid payments.mercado-pago.notification-url");
    }
    if (!"https".equals(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null)
      throw new IllegalStateException("payments.mercado-pago.notification-url must be HTTPS");
    return uri;
  }
}
