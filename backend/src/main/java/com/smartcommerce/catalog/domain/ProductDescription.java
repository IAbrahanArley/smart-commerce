package com.smartcommerce.catalog.domain;

import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/** Fixed formatting contract: no attributes, links, media or executable markup. */
public final class ProductDescription {
  private ProductDescription() {}

  public static String sanitize(String raw) {
    if (raw == null) return "";
    if (raw.codePointCount(0, raw.length()) > 20000
        || raw.getBytes(StandardCharsets.UTF_8).length > 80000)
      throw new IllegalArgumentException("Description exceeds limit");
    if (raw.codePoints().anyMatch(c -> Character.getType(c) == Character.SURROGATE || c == 0))
      throw new IllegalArgumentException("Invalid Unicode");
    String clean =
        Jsoup.clean(
            raw,
            "",
            new Safelist().addTags("p", "br", "strong", "em", "ul", "ol", "li"),
            new Document.OutputSettings().prettyPrint(false));
    if (clean.codePointCount(0, clean.length()) > 20000
        || clean.getBytes(StandardCharsets.UTF_8).length > 80000)
      throw new IllegalArgumentException("Sanitized description exceeds limit");
    return clean;
  }
}
