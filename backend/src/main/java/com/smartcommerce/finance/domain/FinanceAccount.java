package com.smartcommerce.finance.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * An account of the store and its balance, the sum of its movements (SC-050, ADR-0019 C2). {@code
 * systemKey} is set on the accounts the platform keeps itself (default drawer, Mercado Pago).
 */
public record FinanceAccount(
    UUID id,
    String type,
    String name,
    String systemKey,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {}
