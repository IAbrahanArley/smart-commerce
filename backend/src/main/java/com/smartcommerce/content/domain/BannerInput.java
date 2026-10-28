package com.smartcommerce.content.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * A home banner as the owner describes it (SC-043, ADR-0018 V8). Alternative text is required; the
 * link is internal only (a category or product address, or a search), so a banner can never send
 * buyers to another site; the optional period limits when it shows.
 */
public record BannerInput(
    String alt,
    String title,
    LinkKind linkKind,
    String linkTarget,
    Instant startsAt,
    Instant endsAt) {
  public enum LinkKind {
    NONE,
    CATEGORY,
    PRODUCT,
    SEARCH
  }

  public static BannerInput of(
      String alt,
      String title,
      String linkKind,
      String linkTarget,
      Instant startsAt,
      Instant endsAt) {
    String text = line(alt, 200);
    if (text == null) throw new IllegalArgumentException("Alternative text is required");
    LinkKind kind =
        linkKind == null || linkKind.isBlank()
            ? LinkKind.NONE
            : LinkKind.valueOf(linkKind.strip().toUpperCase(Locale.ROOT));
    String target =
        switch (kind) {
          case NONE -> null;
          case CATEGORY, PRODUCT -> slug(linkTarget);
          case SEARCH -> required(line(linkTarget, 100));
        };
    if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt))
      throw new IllegalArgumentException("Period ends before it starts");
    return new BannerInput(text, line(title, 80), kind, target, startsAt, endsAt);
  }

  private static String slug(String raw) {
    String value = required(raw == null ? null : raw.strip().toLowerCase(Locale.ROOT));
    if (value.length() > 80 || !value.matches("[a-z0-9]+(-[a-z0-9]+)*"))
      throw new IllegalArgumentException("Invalid address");
    return value;
  }

  private static String required(String value) {
    if (value == null || value.isEmpty())
      throw new IllegalArgumentException("Link target required");
    return value;
  }

  private static String line(String raw, int max) {
    if (raw == null) return null;
    String value = raw.strip().replaceAll(" {2,}", " ");
    if (value.isEmpty()) return null;
    if (value.length() > max || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid text");
    return value;
  }
}
