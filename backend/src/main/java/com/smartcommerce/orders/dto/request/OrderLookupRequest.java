package com.smartcommerce.orders.dto.request;

import com.smartcommerce.identity.domain.EmailAddress;
import java.util.Locale;

/**
 * "Acompanhar pedido" (SC-046): the order number as the buyer typed it ("#123", "nº 123" or "123")
 * and the e-mail of the order. Sent in the body, never in the URL, so the e-mail stays out of
 * access logs.
 */
public record OrderLookupRequest(String number, String email) {
  public long orderNumber() {
    String digits =
        number == null ? "" : number.strip().replaceFirst("(?i)^(?:n[º°o.]?|#)\\s*", "");
    if (!digits.matches("[1-9][0-9]{0,11}")) throw new IllegalArgumentException("Order number");
    return Long.parseLong(digits);
  }

  /** The same key the order stored for its buyer: spaces and letter case do not matter. */
  public String emailKey() {
    if (email == null || email.length() > 320) throw new IllegalArgumentException("E-mail");
    return EmailAddress.parse(email.strip()).comparisonKey().toLowerCase(Locale.ROOT);
  }
}
