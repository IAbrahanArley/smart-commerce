package com.smartcommerce.finance.dto.request;

/** A new drawer or bank account (SC-050); {@code requestId} makes a retry the same account. */
public record AccountRequest(String requestId, String type, String name) {}
