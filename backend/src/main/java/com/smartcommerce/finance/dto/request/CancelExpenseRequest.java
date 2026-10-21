package com.smartcommerce.finance.dto.request;

/** Reason to cancel (or correct) an expense: one line, 3 to 300 characters (SC-020). */
public record CancelExpenseRequest(String reason) {}
