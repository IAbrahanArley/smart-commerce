package com.smartcommerce.finance.dto.request;

/**
 * Payment of a payable (SC-051), validated by {@code ExpensePayment}: day as {@code yyyy-MM-dd},
 * the account the money left, and optional interest or fine and discount as text with up to two
 * decimals.
 */
public record PayExpenseRequest(
    String paidOn, String accountId, String surcharge, String discount) {}
