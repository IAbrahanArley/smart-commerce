package com.smartcommerce.advisor.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * What one question consumed, recorded when it finishes (ADR-0016 A9): outcome, model, token
 * counts, tool calls and the estimated cost. Never the text of the question or of the answer.
 */
public record AdvisorUsage(
    String outcome,
    String model,
    int inputTokens,
    int cacheReadTokens,
    int outputTokens,
    int toolCalls,
    String toolsUsed,
    BigDecimal estimatedCostUsd) {
  public static final Set<String> OUTCOMES =
      Set.of("ANSWERED", "REFUSED", "UNGROUNDED", "PROVIDER_ERROR", "CANCELLED");

  public AdvisorUsage {
    if (!OUTCOMES.contains(outcome)) throw new IllegalArgumentException("Invalid outcome");
    if (inputTokens < 0 || cacheReadTokens < 0 || outputTokens < 0 || toolCalls < 0)
      throw new IllegalArgumentException("Negative usage");
    if (estimatedCostUsd == null || estimatedCostUsd.signum() < 0)
      throw new IllegalArgumentException("Invalid cost");
    if (model != null && model.length() > 60) throw new IllegalArgumentException("Model too long");
    if (toolsUsed != null && toolsUsed.length() > 200)
      throw new IllegalArgumentException("Tools too long");
  }
}
