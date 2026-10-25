package com.smartcommerce.advisor.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.smartcommerce.advisor.domain.AdvisorException;

/**
 * The Claude API client (SC-026, ADR-0016 A1), present only when the API key is configured. The key
 * stays inside the SDK client: it is never logged nor returned, and the base URL is configurable
 * only for the loopback fake used by tests.
 */
public final class ClaudeClient {
  private final AnthropicClient client;
  private final String model;

  public ClaudeClient(AnthropicClient client, String model) {
    this.client = client;
    this.model = model;
  }

  public static ClaudeClient unavailable(String model) {
    return new ClaudeClient(null, model);
  }

  public boolean available() {
    return client != null;
  }

  public String model() {
    return model;
  }

  public Message create(MessageCreateParams params) {
    if (client == null) throw new AdvisorException(AdvisorException.Reason.DISABLED);
    return client.messages().create(params);
  }
}
