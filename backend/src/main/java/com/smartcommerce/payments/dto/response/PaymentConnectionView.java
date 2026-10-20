package com.smartcommerce.payments.dto.response;

import java.time.Instant;

/**
 * What the OWNER sees about the connection. Never a token: the account appears only by the
 * provider's numeric id. {@code status}: UNAVAILABLE (not configured here), NOT_CONNECTED,
 * CONNECTED, EXPIRED (access token past its expiry; reconnect) or REVOKED (provider withdrew it).
 */
public record PaymentConnectionView(
    String provider,
    String status,
    String accountId,
    Instant connectedAt,
    Instant accessExpiresAt,
    Instant refreshedAt,
    Instant closedAt) {}
