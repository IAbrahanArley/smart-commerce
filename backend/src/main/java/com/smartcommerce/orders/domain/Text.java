package com.smartcommerce.orders.domain;

import java.text.Normalizer;

/** Single-line buyer text: trimmed, NFC, bounded by code points, no controls or line breaks. */
final class Text {
  private Text() {}

  static String required(String value, int max) {
    String text = optional(value, max);
    if (text == null) throw new IllegalArgumentException("Required text");
    return text;
  }

  static String optional(String value, int max) {
    if (value == null) return null;
    String text = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    if (text.isEmpty()) return null;
    if (text.codePointCount(0, text.length()) > max
        || text.codePoints()
            .anyMatch(
                c ->
                    Character.isISOControl(c)
                        || Character.getType(c) == Character.LINE_SEPARATOR
                        || Character.getType(c) == Character.PARAGRAPH_SEPARATOR
                        || Character.getType(c) == Character.SURROGATE))
      throw new IllegalArgumentException("Invalid text");
    return text;
  }
}
