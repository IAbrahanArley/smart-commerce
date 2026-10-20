package com.smartcommerce.payments.domain;

import java.time.Instant;

/** A stored provider account. Token columns stay encrypted; only the services decrypt them. */
public record PaymentAccountRecord(
    String providerUserId,
    String status,
    byte[] accessToken,
    byte[] refreshToken,
    Instant accessExpiresAt,
    Instant connectedAt,
    Instant refreshedAt,
    Instant closedAt) {
  @Override
  public String toString() {
    return "PaymentAccountRecord[" + status + "]";
  }
}
