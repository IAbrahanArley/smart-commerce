package com.smartcommerce.customers.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer of the store (SC-055, ADR-0021 R2): name, phone with digits only (an optional leading
 * "+"), optional e-mail and note, and an optional store credit limit (null = no limit). Personal
 * data: never logged.
 */
public record StoreCustomer(
    UUID id,
    UUID requestId,
    String name,
    String phone,
    String email,
    String note,
    BigDecimal creditLimit,
    Instant createdAt) {

  /** The phone as an employee who only operates the counter sees it: its last 4 digits. */
  public String maskedPhone() {
    return mask(phone);
  }

  public static String mask(String phone) {
    if (phone == null || phone.length() < 4) return "••••";
    return "•••• " + phone.substring(phone.length() - 4);
  }
}
