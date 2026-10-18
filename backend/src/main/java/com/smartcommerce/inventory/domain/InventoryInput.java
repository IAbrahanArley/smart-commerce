package com.smartcommerce.inventory.domain;

import java.util.UUID;

public record InventoryInput(UUID requestId, MovementType type, int delta, String reason) {
  public enum MovementType {
    ENTRY,
    ADJUSTMENT
  }

  public InventoryInput {
    if (requestId == null
        || type == null
        || delta == 0
        || (type == MovementType.ENTRY && delta < 0))
      throw new IllegalArgumentException("Invalid movement");
    if (reason == null
        || reason.length() > 960
        || reason
            .codePoints()
            .anyMatch(
                code ->
                    Character.isISOControl(code)
                        || Character.getType(code) == Character.FORMAT
                        || Character.getType(code) == Character.SURROGATE
                        || Character.getType(code) == Character.LINE_SEPARATOR
                        || Character.getType(code) == Character.PARAGRAPH_SEPARATOR))
      throw new IllegalArgumentException("Invalid reason");
    reason = reason.strip();
    if (reason.isBlank() || reason.codePointCount(0, reason.length()) > 240)
      throw new IllegalArgumentException("Invalid reason");
  }

  public static InventoryInput parse(UUID requestId, String type, int delta, String reason) {
    return new InventoryInput(
        requestId, MovementType.valueOf(type == null ? "" : type), delta, reason);
  }

  public static Long cursor(String before) {
    if (before == null) return null;
    if (!before.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid cursor");
    try {
      return Long.valueOf(before);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid cursor");
    }
  }

  public int balanceAfter(int current) {
    if (current < 0) throw new IllegalArgumentException("Invalid current quantity");
    long next = (long) current + delta;
    if (next < 0) throw new InventoryException(InventoryException.Reason.INSUFFICIENT_STOCK);
    if (next > Integer.MAX_VALUE)
      throw new InventoryException(InventoryException.Reason.BALANCE_LIMIT);
    return (int) next;
  }
}
