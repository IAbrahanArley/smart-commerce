package com.smartcommerce.finance.dto.request;

/**
 * Store OWNER expense or payable body (SC-020, SC-051), validated by {@code ExpenseInput}: amount
 * as text with up to two decimals (the total, with installments), dates as {@code yyyy-MM-dd}.
 * Optional: {@code dueDate} (defaults to the competence), supplier, document number, {@code
 * installments} (1 to 24), {@code recurrence} ({@code NONE} or {@code MONTHLY}) and, when already
 * paid, {@code paidOn} together with {@code paidAccountId}.
 */
public record ExpenseRequest(
    String requestId,
    String category,
    String kind,
    String description,
    String amount,
    String competenceDate,
    String dueDate,
    String supplier,
    String documentNumber,
    Integer installments,
    String recurrence,
    String paidOn,
    String paidAccountId) {}
