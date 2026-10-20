package com.smartcommerce.payments.domain;

import java.time.Instant;

/** Tokens returned by the provider. {@link #toString()} never prints them. */
public record OAuthTokens(
    String accessToken,
    String refreshToken,
    String providerUserId,
    Instant accessExpiresAt,
    Boolean liveMode) {
  @Override
  public String toString() {
    return "OAuthTokens[redacted]";
  }
}
