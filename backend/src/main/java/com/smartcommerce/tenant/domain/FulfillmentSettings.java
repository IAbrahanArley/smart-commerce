package com.smartcommerce.tenant.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validated delivery options (ADR-0012 D3). Disabled options keep their texts and amount so the
 * owner can re-enable them; an enabled option always has the text the buyer needs.
 */
public record FulfillmentSettings(
    boolean pickupEnabled,
    String pickupInstructions,
    boolean fixedShippingEnabled,
    BigDecimal fixedShippingAmount,
    String fixedShippingArea) {
  public static final int TEXT_LIMIT = 500;

  public static FulfillmentSettings disabled() {
    return new FulfillmentSettings(false, null, false, null, null);
  }

  public static FulfillmentSettings parse(
      boolean pickupEnabled,
      String pickupInstructions,
      boolean fixedShippingEnabled,
      String fixedShippingAmount,
      String fixedShippingArea) {
    String instructions = text(pickupInstructions);
    String area = text(fixedShippingArea);
    BigDecimal amount = fixedShippingAmount == null ? null : amount(fixedShippingAmount);
    if (pickupEnabled && instructions == null)
      throw new IllegalArgumentException("Pickup requires instructions");
    if (fixedShippingEnabled && (amount == null || area == null))
      throw new IllegalArgumentException("Fixed shipping requires amount and area");
    return new FulfillmentSettings(pickupEnabled, instructions, fixedShippingEnabled, amount, area);
  }

  /** Same textual BRL contract as the catalog, except that zero means free shipping. */
  private static BigDecimal amount(String input) {
    if (!input.matches("[0-9]{1,17}(?:\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid BRL amount");
    return new BigDecimal(input).setScale(2, RoundingMode.UNNECESSARY);
  }

  private static String text(String value) {
    if (value == null) return null;
    if (value.length() > TEXT_LIMIT * 4
        || value.codePoints().anyMatch(FulfillmentSettings::invalidText))
      throw new IllegalArgumentException("Invalid fulfillment text");
    String result = value.strip();
    if (result.isEmpty()) return null;
    if (result.codePointCount(0, result.length()) > TEXT_LIMIT)
      throw new IllegalArgumentException("Invalid fulfillment text");
    return result;
  }

  private static boolean invalidText(int code) {
    int type = Character.getType(code);
    return Character.isISOControl(code)
        || type == Character.FORMAT
        || type == Character.SURROGATE
        || type == Character.LINE_SEPARATOR
        || type == Character.PARAGRAPH_SEPARATOR;
  }
}
