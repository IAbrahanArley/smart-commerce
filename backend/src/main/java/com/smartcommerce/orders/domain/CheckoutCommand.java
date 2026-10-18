package com.smartcommerce.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validated public checkout. {@code expectedTotal} is the total the buyer confirmed; the backend
 * recomputes it and never accepts a price from the client. The fingerprint identifies the payload
 * behind a {@code requestId} for idempotent replays (ADR-0012 §6).
 */
public record CheckoutCommand(
    UUID requestId,
    List<CheckoutLine> lines,
    FulfillmentType fulfillment,
    ShippingAddress address,
    OrderBuyer buyer,
    BigDecimal expectedTotal) {
  public CheckoutCommand {
    if (requestId == null
        || lines == null
        || lines.isEmpty()
        || lines.size() > CheckoutLine.MAX_LINES
        || lines.contains(null)
        || fulfillment == null
        || buyer == null
        || expectedTotal == null
        || expectedTotal.signum() <= 0
        || expectedTotal.scale() > 2) throw new IllegalArgumentException("Invalid checkout");
    if (new HashSet<>(lines.stream().map(CheckoutLine::variantId).toList()).size() != lines.size())
      throw new IllegalArgumentException("Duplicate checkout line");
    if ((fulfillment == FulfillmentType.FIXED_SHIPPING) != (address != null))
      throw new IllegalArgumentException("Address only for fixed shipping");
    lines = List.copyOf(lines);
  }

  public List<UUID> variantIds() {
    return lines.stream().map(CheckoutLine::variantId).toList();
  }

  public String fingerprint() {
    String canonical =
        String.join(
            "\n\u0000",
            lines.stream()
                .sorted(Comparator.comparing(CheckoutLine::variantId))
                .map(line -> line.variantId() + ":" + line.quantity())
                .collect(Collectors.joining(",")),
            fulfillment.name(),
            address == null ? "" : address.canonical(),
            buyer.canonical(),
            expectedTotal.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    return sha256(canonical);
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
