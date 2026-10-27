package com.smartcommerce.catalog.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The address of a category in the storefront (ADR-0018 V1/V5): lower-case letters and digits in
 * groups joined by single hyphens, 1 to 80 characters, unique in the store.
 */
public record CategorySlug(String value) {
  private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final int MAX = 80;

  /** Room left for a "-N" suffix when two names fold to the same slug. */
  private static final int GENERATED_MAX = 60;

  public CategorySlug {
    if (value == null || value.length() > MAX || !FORMAT.matcher(value).matches())
      throw new IllegalArgumentException("Invalid category slug");
  }

  /** A slug typed by the owner; surrounding spaces and upper case are forgiven. */
  public static CategorySlug parse(String raw) {
    if (raw == null) throw new IllegalArgumentException("Invalid category slug");
    return new CategorySlug(raw.strip().toLowerCase(Locale.ROOT));
  }

  /**
   * The slug derived from a name, accents folded: "Óculos & Viseiras" is "oculos-viseiras". When
   * {@code taken} already has it, "-2", "-3"... are tried.
   */
  public static CategorySlug from(String name, Predicate<String> taken) {
    String base =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    if (base.length() > GENERATED_MAX) base = base.substring(0, GENERATED_MAX).replaceAll("-$", "");
    if (base.isEmpty()) base = "categoria";
    String candidate = base;
    for (int n = 2; taken.test(candidate); n++) candidate = base + "-" + n;
    return new CategorySlug(candidate);
  }
}
