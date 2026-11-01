package com.smartcommerce.billing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a plan change does before the owner confirms it (ADR-0017 B13). {@code amountDue} is what
 * Stripe charges now, with its currency (both null when the change waits for the period's end);
 * {@code prorationDate} goes back in the confirmation. {@code exceedsCompanies} warns that the
 * owner has more stores than the new plan allows: nothing is removed, new ones are blocked.
 */
public record ChangePreview(
    String timing,
    String plan,
    String cycle,
    BigDecimal amountDue,
    String currency,
    Instant effectiveAt,
    Long prorationDate,
    boolean exceedsCompanies) {}
