package com.smartcommerce.finance.dto.request;

/**
 * Store OWNER expense body (SC-020), validated by {@code ExpenseInput}: amount as text with up to
 * two decimals, dates as {@code yyyy-MM-dd}, {@code paidOn} optional.
 */
public record ExpenseRequest(
    String requestId,
    String category,
    String kind,
    String description,
    String amount,
    String competenceDate,
    String paidOn) {}
