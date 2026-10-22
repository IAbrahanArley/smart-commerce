package com.smartcommerce.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One ledger fact (SC-019, ADR-0015 F7). {@code amount} is signed: income positive, costs and
 * deductions negative, always BRL with two decimals at most.
 */
public record FinanceEntry(
    String key,
    String type,
    BigDecimal amount,
    Instant competenceAt,
    Instant settledAt,
    UUID orderId,
    boolean costIncomplete) {}
