package com.smartcommerce.payments.provider;

import com.smartcommerce.payments.domain.OAuthTokens;
import com.smartcommerce.payments.domain.PaymentException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mercado Pago OAuth (ADR-0013 P3): authorization URL with PKCE S256 and the {@code /oauth/token}
 * exchange and refresh. Endpoints are configurable only so tests can use a loopback fake; they must
 * be HTTPS otherwise. The client secret, codes and tokens never reach logs or exception messages,
 * and redirects are never followed.
 */
public final class MercadoPagoOAuthClient {
  public static final String DEFAULT_AUTHORIZATION_URL =
      "https://auth.mercadopago.com/authorization";
  public static final String DEFAULT_TOKEN_URL = "https://api.mercadopago.com/oauth/token";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final long MAX_EXPIRES_IN = Duration.ofDays(400).toSeconds();
  private static final int MAX_TOKEN_LENGTH = 512;
  private final String clientId;
  private final String clientSecret;
  private final URI redirectUri;
  private final URI authorizationUrl;
  private final URI tokenUrl;
  private final boolean testToken;
  private final HttpClient http;

  public MercadoPagoOAuthClient(
      String clientId,
      String clientSecret,
      String redirectUri,
      String authorizationUrl,
      String tokenUrl,
      boolean testToken) {
    if (clientId == null || !clientId.strip().matches("[0-9]{1,40}"))
      throw new IllegalStateException("payments.mercado-pago.client-id must be the numeric APP_ID");
    if (clientSecret == null || clientSecret.isBlank())
      throw new IllegalStateException("payments.mercado-pago.client-secret is required");
    this.clientId = clientId.strip();
    this.clientSecret = clientSecret.strip();
    this.redirectUri = endpoint(redirectUri, null, "redirect-uri");
    this.authorizationUrl =
        endpoint(authorizationUrl, DEFAULT_AUTHORIZATION_URL, "authorization-url");
    this.tokenUrl = endpoint(tokenUrl, DEFAULT_TOKEN_URL, "token-url");
    this.testToken = testToken;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /** Where the browser goes; the state and the challenge are the only per-attempt values. */
  public String authorizationUrl(String state, String codeChallenge) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("client_id", clientId);
    query.put("response_type", "code");
    query.put("platform_id", "mp");
    query.put("state", state);
    query.put("redirect_uri", redirectUri.toString());
    query.put("code_challenge", codeChallenge);
    query.put("code_challenge_method", "S256");
    StringBuilder url = new StringBuilder(authorizationUrl.toString()).append('?');
    query.forEach(
        (name, value) ->
            url.append(name)
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
                .append('&'));
    return url.substring(0, url.length() - 1);
  }

  public OAuthTokens exchange(String code, String codeVerifier, Instant now) {
    Map<String, Object> body = base("authorization_code");
    body.put("code", code);
    body.put("redirect_uri", redirectUri.toString());
    body.put("code_verifier", codeVerifier);
    body.put("test_token", String.valueOf(testToken));
    return post(body, now, false);
  }

  /** The provider rotates the refresh token: the caller must store the new one. */
  public OAuthTokens refresh(String refreshToken, Instant now) {
    Map<String, Object> body = base("refresh_token");
    body.put("refresh_token", refreshToken);
    return post(body, now, true);
  }

  private Map<String, Object> base(String grant) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("client_id", clientId);
    body.put("client_secret", clientSecret);
    body.put("grant_type", grant);
    return body;
  }

  private OAuthTokens post(Map<String, Object> body, Instant now, boolean refreshing) {
    HttpResponse<String> response;
    try {
      response =
          http.send(
              HttpRequest.newBuilder(tokenUrl)
                  .timeout(TIMEOUT)
                  .header("Content-Type", "application/json")
                  .header("Accept", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    } catch (IOException failure) {
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    JsonNode json = parse(response.body());
    if (response.statusCode() != 200) {
      // invalid_grant on refresh: the seller revoked the app or the refresh token expired.
      boolean invalidGrant =
          json != null
              && json.get("error") != null
              && "invalid_grant".equals(json.get("error").asString());
      if (refreshing && invalidGrant && response.statusCode() >= 400 && response.statusCode() < 500)
        throw new PaymentException(PaymentException.Reason.REVOKED);
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    }
    return tokens(json, now);
  }

  private static OAuthTokens tokens(JsonNode json, Instant now) {
    if (json == null) throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    String access = text(json, "access_token");
    String refresh = text(json, "refresh_token");
    JsonNode user = json.get("user_id");
    JsonNode expires = json.get("expires_in");
    String userId = user == null || user.isNull() ? null : user.asString();
    if (access == null
        || refresh == null
        || userId == null
        || !userId.matches("[0-9]{1,40}")
        || expires == null
        || !expires.isIntegralNumber()
        || expires.asLong() <= 0
        || expires.asLong() > MAX_EXPIRES_IN)
      throw new PaymentException(PaymentException.Reason.PROVIDER_ERROR);
    JsonNode live = json.get("live_mode");
    return new OAuthTokens(
        access,
        refresh,
        userId,
        now.plusSeconds(expires.asLong()),
        live != null && live.isBoolean() ? live.asBoolean() : null);
  }

  private static String text(JsonNode json, String field) {
    JsonNode node = json.get(field);
    if (node == null || !node.isString()) return null;
    String value = node.asString();
    return value.isBlank() || value.length() > MAX_TOKEN_LENGTH || !value.matches("[\\x21-\\x7e]+")
        ? null
        : value;
  }

  private static JsonNode parse(String body) {
    if (body == null || body.length() > 16_384) return null;
    try {
      JsonNode node = JSON.readTree(body);
      return node != null && node.isObject() ? node : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  static URI endpoint(String configured, String fallback, String name) {
    String value = configured == null || configured.isBlank() ? fallback : configured.strip();
    if (value == null)
      throw new IllegalStateException("payments.mercado-pago." + name + " is required");
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid payments.mercado-pago." + name);
    }
    boolean loopback = "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost());
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)))
      throw new IllegalStateException(
          "payments.mercado-pago." + name + " must be HTTPS (HTTP only on loopback)");
    return uri;
  }
}
