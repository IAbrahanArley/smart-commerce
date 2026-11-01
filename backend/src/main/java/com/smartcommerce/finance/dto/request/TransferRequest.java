package com.smartcommerce.finance.dto.request;

/** Money moved between two accounts of the store (SC-050); amount as a positive decimal string. */
public record TransferRequest(
    String requestId,
    String fromAccountId,
    String toAccountId,
    String amount,
    String occurredOn,
    String note) {}
