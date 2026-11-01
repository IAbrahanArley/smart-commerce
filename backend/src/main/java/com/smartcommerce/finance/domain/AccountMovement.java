package com.smartcommerce.finance.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One movement of an account's statement, with the balance right after it (SC-050); money as text.
 */
public record AccountMovement(
    UUID id,
    String kind,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
    LocalDate occurredOn,
    String description,
    UUID transferId,
    UUID counterpartAccountId,
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceAfter) {}
