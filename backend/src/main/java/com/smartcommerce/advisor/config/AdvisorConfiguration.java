package com.smartcommerce.advisor.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.smartcommerce.advisor.domain.AdvisorPricing;
import com.smartcommerce.advisor.provider.ClaudeClient;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SC-026 (ADR-0016 A1, A2, A11): the model client exists only when {@code ANTHROPIC_API_KEY} is
 * set; the assistant also needs {@code ADVISOR_ENABLED=true}. The model must have a known price, so
 * every question's cost can be estimated against the monthly cap. The base URL is for the loopback
 * fake of the tests only and must be an origin.
 */
@Configuration(proxyBeanMethods = false)
public class AdvisorConfiguration {
  /** One request; the question's own budget is the number of rounds (A8). */
  static final Duration TIMEOUT = Duration.ofSeconds(60);

  @Bean
  ClaudeClient claudeClient(
      @Value("${advisor.model:claude-opus-5-5}") String model,
      @Value("${advisor.anthropic.api-key:}") String apiKey,
      @Value("${advisor.anthropic.base-url:}") String baseUrl) {
    AdvisorPricing.of(model); // fails fast on a model without a known price
    if (apiKey == null || apiKey.isBlank()) return ClaudeClient.unavailable(model);
    AnthropicOkHttpClient.Builder builder =
        AnthropicOkHttpClient.builder().apiKey(apiKey.strip()).timeout(TIMEOUT).maxRetries(1);
    if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(origin(baseUrl.strip()));
    return new ClaudeClient(builder.build(), model);
  }

  private static String origin(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("Invalid advisor.anthropic.base-url");
    }
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
        || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
      throw new IllegalStateException("advisor.anthropic.base-url must be an origin");
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
