package com.smartcommerce.orders.domain;

import com.smartcommerce.identity.domain.EmailAddress;
import java.util.Locale;

/**
 * Minimal buyer data (ADR-0012 D2): name, e-mail and phone, stored only as an order snapshot and
 * never logged. {@code emailKey} groups pending orders per store for the D4 limit; unlike the
 * identity login key it ignores case in the local part too, so changing letter case cannot bypass
 * the limit.
 */
public record OrderBuyer(String name, String email, String emailKey, String phone) {
  public static OrderBuyer of(String name, String email, String phone) {
    EmailAddress address = EmailAddress.parse(email == null ? null : email.strip());
    String digits = phone == null ? null : phone.replaceAll("[\\s().-]", "");
    if (digits == null || !digits.matches("\\+?[0-9]{10,15}"))
      throw new IllegalArgumentException("Invalid phone");
    return new OrderBuyer(
        Text.required(name, 120),
        address.original(),
        address.comparisonKey().toLowerCase(Locale.ROOT),
        digits);
  }

  String canonical() {
    return String.join("\n", name, emailKey, phone);
  }
}
