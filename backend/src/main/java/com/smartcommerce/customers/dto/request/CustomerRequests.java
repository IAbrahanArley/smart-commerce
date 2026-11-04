package com.smartcommerce.customers.dto.request;

/**
 * Store customer bodies (SC-055), validated by {@code CustomerInput}; the limit as decimal text.
 */
public final class CustomerRequests {
  private CustomerRequests() {}

  /** The search text travels in the body, so it never reaches an access log (ADR-0021). */
  public record Search(String q) {}

  /** An employee sends only name and phone; e-mail, note and limit are the OWNER's. */
  public record Customer(
      String requestId, String name, String phone, String email, String note, String creditLimit) {}
}
