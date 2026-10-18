package com.smartcommerce.catalog.domain;

import java.text.Normalizer;

public record VariantOption(String name, String value) {
  public VariantOption {
    name = label(name);
    value = label(value);
  }

  private static String label(String raw) {
    if (raw == null || raw.length() > 160) throw new IllegalArgumentException("Invalid option");
    String clean = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
    if (clean.isEmpty()
        || clean.codePointCount(0, clean.length()) > 40
        || clean
            .codePoints()
            .anyMatch(
                c ->
                    Character.isISOControl(c)
                        || Character.getType(c) == Character.FORMAT
                        || Character.getType(c) == Character.SURROGATE
                        || Character.getType(c) == Character.LINE_SEPARATOR
                        || Character.getType(c) == Character.PARAGRAPH_SEPARATOR))
      throw new IllegalArgumentException("Invalid option");
    return clean;
  }
}
