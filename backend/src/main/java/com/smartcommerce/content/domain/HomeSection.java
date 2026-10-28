package com.smartcommerce.content.domain;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One block of the store home (SC-043, ADR-0018 V8), of a fixed kind: the newest products, the best
 * sellers, the products marked as featured, or one category. {@code categoryId} only for CATEGORY.
 */
public record HomeSection(Kind kind, String title, UUID categoryId) {
  public static final int MAX = 8;

  public enum Kind {
    NEW,
    BEST,
    FEATURED,
    CATEGORY
  }

  public static HomeSection of(String kind, String title, UUID categoryId) {
    if (kind == null) throw new IllegalArgumentException("Section kind");
    Kind parsed = Kind.valueOf(kind.strip().toUpperCase(Locale.ROOT));
    if ((parsed == Kind.CATEGORY) != (categoryId != null))
      throw new IllegalArgumentException("Category sections need a category, and only they");
    String text = title == null ? "" : title.strip().replaceAll(" {2,}", " ");
    if (text.isEmpty() || text.length() > 60 || text.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Section title");
    return new HomeSection(parsed, text, categoryId);
  }

  /** At most {@link #MAX} sections; the order of the list is the order of the home. */
  public static List<HomeSection> list(List<HomeSection> sections) {
    if (sections == null || sections.size() > MAX)
      throw new IllegalArgumentException("Too many sections");
    return List.copyOf(sections);
  }
}
