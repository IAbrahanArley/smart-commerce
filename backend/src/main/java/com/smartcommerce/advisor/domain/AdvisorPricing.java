package com.smartcommerce.advisor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Claude API prices in US$ per million tokens (ADR-0016, confirmed on 2026-10-25), used only to
 * estimate each question's cost against the monthly cap (A8); the provider's invoice is the truth.
 * Cache writes use the 5-minute TTL (1.25× input).
 */
public record AdvisorPricing(
    BigDecimal input, BigDecimal cacheWrite, BigDecimal cacheRead, BigDecimal output) {
  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  public static AdvisorPricing of(String model) {
    return switch (model) {
      case "claude-opus-5-5" -> prices("4.00", "5.00", "0.20", "20.00");
      case "claude-sonnet-5-5" -> prices("2.00", "2.50", "0.20", "10.00");
      case "claude-haiku-5-5" -> prices("0.10", "0.125", "0.01", "0.50");
      default -> throw new IllegalStateException("advisor.model has no known price");
    };
  }

  /** Estimated cost in US$, six decimals (the column's scale). */
  public BigDecimal cost(long inputTokens, long cacheWriteTokens, long cacheReadTokens, long out) {
    return input
        .multiply(BigDecimal.valueOf(inputTokens))
        .add(cacheWrite.multiply(BigDecimal.valueOf(cacheWriteTokens)))
        .add(cacheRead.multiply(BigDecimal.valueOf(cacheReadTokens)))
        .add(output.multiply(BigDecimal.valueOf(out)))
        .divide(MILLION, 6, RoundingMode.HALF_EVEN);
  }

  private static AdvisorPricing prices(String in, String write, String read, String out) {
    return new AdvisorPricing(
        new BigDecimal(in), new BigDecimal(write), new BigDecimal(read), new BigDecimal(out));
  }
}
