package com.smartcommerce.storefront.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tells storefront-web to drop its cached pages of a store (SC-047, ADR-0018 V9). The request is
 * signed with HMAC-SHA256 over the store slug and a timestamp, with a secret shared only by the two
 * servers; storefront-web refuses unsigned, stale or wrongly signed calls. Without the URL or the
 * secret the client does nothing and pages refresh on their own within 60 seconds.
 */
@Component
public class StorefrontRevalidationClient {
  private static final Logger LOG = LoggerFactory.getLogger(StorefrontRevalidationClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private final URI endpoint;
  private final byte[] secret;
  private final Clock clock;
  private final HttpClient client;

  public StorefrontRevalidationClient(
      @Value("${storefront.revalidation.url:}") String url,
      @Value("${storefront.revalidation.secret:}") String secret,
      Clock clock) {
    this.endpoint = endpoint(url);
    this.secret =
        secret == null || secret.length() < 32 ? null : secret.getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    if ((this.endpoint == null) != (this.secret == null))
      LOG.warn(
          "Storefront revalidation needs both storefront.revalidation.url and a secret of"
              + " at least 32 characters; cached pages will refresh by time only");
  }

  public boolean enabled() {
    return endpoint != null && secret != null;
  }

  /** Best effort: a failure only means the pages refresh by time (60 s). */
  public void revalidate(String slug) {
    if (!enabled()) return;
    String timestamp = String.valueOf(clock.instant().getEpochSecond());
    try {
      HttpResponse<Void> response =
          client.send(
              HttpRequest.newBuilder(endpoint)
                  .timeout(TIMEOUT)
                  .header("Content-Type", "application/json")
                  .header("X-SC-Revalidate-Timestamp", timestamp)
                  .header("X-SC-Revalidate-Signature", sign(slug + "\n" + timestamp))
                  .POST(HttpRequest.BodyPublishers.ofString("{\"store\":\"" + slug + "\"}"))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 204)
        LOG.warn("Storefront revalidation answered {}", response.statusCode());
    } catch (IOException failure) {
      LOG.warn("Storefront revalidation unavailable: {}", failure.getClass().getSimpleName());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  String sign(String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static URI endpoint(String url) {
    if (url == null || url.isBlank()) return null;
    URI base = URI.create(url.strip());
    if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null)
      throw new IllegalStateException("storefront.revalidation.url must be an http(s) origin");
    return URI.create(base.getScheme() + "://" + base.getRawAuthority() + "/interno/revalidar");
  }
}
