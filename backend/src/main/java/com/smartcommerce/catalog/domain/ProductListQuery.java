package com.smartcommerce.catalog.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin product list input, validated before any query: search words (matched against the name, the
 * SKU and the variant SKUs, ignoring case and accents), the main category (or products without
 * one), the storefront visibility, the order and the 1-based page.
 */
public record ProductListQuery(
    List<String> words,
    UUID category,
    boolean withoutCategory,
    Visibility visibility,
    Sort sort,
    int page) {
  public static final int PAGE_SIZE = 25;
  public static final String WITHOUT_CATEGORY = "none";

  public enum Visibility {
    ALL,
    VISIBLE,
    HIDDEN
  }

  public enum Sort {
    DEFAULT,
    NAME,
    PRICE_ASC,
    PRICE_DESC
  }

  public static ProductListQuery of(
      String search, String category, String visibility, String sort, String page) {
    String text = search == null ? "" : search.strip();
    if (text.length() > 200) throw new IllegalArgumentException("Invalid search");
    List<String> words =
        text.isEmpty()
            ? List.of()
            : Arrays.stream(fold(text).split("\\s+")).filter(word -> !word.isEmpty()).toList();
    if (words.size() > 10) throw new IllegalArgumentException("Invalid search");
    UUID categoryId = null;
    boolean without = WITHOUT_CATEGORY.equals(category);
    if (!without && category != null && !category.isBlank()) {
      if (!category.matches("[0-9a-f-]{36}"))
        throw new IllegalArgumentException("Invalid category");
      categoryId = UUID.fromString(category);
    }
    return new ProductListQuery(
        words,
        categoryId,
        without,
        option(Visibility.class, visibility, Visibility.ALL),
        option(Sort.class, sort, Sort.DEFAULT),
        page(page));
  }

  /** Lower case without accents, like the admin's own search (NFD without combining marks). */
  public static String fold(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  private static <E extends Enum<E>> E option(Class<E> type, String value, E fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid option", exception);
    }
  }

  private static int page(String value) {
    if (value == null || value.isBlank()) return 1;
    if (!value.matches("[1-9][0-9]{0,4}")) throw new IllegalArgumentException("Invalid page");
    return Integer.parseInt(value);
  }
}
