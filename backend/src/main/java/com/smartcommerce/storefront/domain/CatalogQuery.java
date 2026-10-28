package com.smartcommerce.storefront.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a buyer asked the storefront catalog for (Sprint 8, SC-042, ADR-0018 V6), validated from
 * query parameters before any SQL is built. Only slugs, bounded decimals, short option texts and
 * search terms reduced to letters and digits get through; anything else is a 400.
 *
 * @param category category slug, or {@code null} for the whole catalog
 * @param brands brand slugs (any of them)
 * @param priceMin inclusive lower bound of the product price, or {@code null}
 * @param priceMax inclusive upper bound of the product price, or {@code null}
 * @param options variant option values by option name (any value of a name, every name)
 * @param search a PostgreSQL {@code to_tsquery} expression of prefix terms, or {@code null}
 * @param text the search as typed, trimmed, for display
 * @param featured only the products the store marked as featured (SC-043, {@code destaque=1})
 */
public record CatalogQuery(
    String category,
    List<String> brands,
    BigDecimal priceMin,
    BigDecimal priceMax,
    Map<String, List<String>> options,
    String search,
    String text,
    CatalogSort sort,
    int page,
    boolean featured) {
  public static final int PAGE_SIZE = 24;
  static final int MAX_PAGE = 500;
  private static final int MAX_FILTERS = 20;
  private static final int MAX_TERMS = 6;
  private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern AMOUNT = Pattern.compile("^[0-9]{1,12}(?:[.,][0-9]{1,2})?$");

  public static CatalogQuery parse(
      String category,
      List<String> brands,
      String priceMin,
      String priceMax,
      List<String> options,
      String text,
      String sort,
      String page) {
    return parse(category, brands, priceMin, priceMax, options, text, sort, page, null);
  }

  public static CatalogQuery parse(
      String category,
      List<String> brands,
      String priceMin,
      String priceMax,
      List<String> options,
      String text,
      String sort,
      String page,
      String featured) {
    if (featured != null && !featured.isBlank() && !"1".equals(featured.strip()))
      throw new IllegalArgumentException("Featured");
    String search = terms(text);
    CatalogSort order =
        sort == null || sort.isBlank()
            ? search == null ? CatalogSort.RECENT : CatalogSort.RELEVANCE
            : CatalogSort.fromParameter(sort);
    if (order == CatalogSort.RELEVANCE && search == null) order = CatalogSort.RECENT;
    BigDecimal min = amount(priceMin), max = amount(priceMax);
    if (min != null && max != null && min.compareTo(max) > 0)
      throw new IllegalArgumentException("Price range");
    return new CatalogQuery(
        category == null || category.isBlank() ? null : slug(category),
        slugs(brands),
        min,
        max,
        options(options),
        search,
        text == null ? null : text.strip(),
        order,
        page(page),
        featured != null && "1".equals(featured.strip()));
  }

  public int offset() {
    return (page - 1) * PAGE_SIZE;
  }

  private static String slug(String value) {
    if (value.length() > 80 || !SLUG.matcher(value).matches())
      throw new IllegalArgumentException("Slug");
    return value;
  }

  private static List<String> slugs(List<String> values) {
    if (values == null) return List.of();
    Set<String> result = new LinkedHashSet<>();
    for (String value : values)
      for (String part : value.split(",")) if (!part.isBlank()) result.add(slug(part.strip()));
    if (result.size() > MAX_FILTERS) throw new IllegalArgumentException("Too many brands");
    return List.copyOf(result);
  }

  private static BigDecimal amount(String value) {
    if (value == null || value.isBlank()) return null;
    if (!AMOUNT.matcher(value.strip()).matches()) throw new IllegalArgumentException("Amount");
    return new BigDecimal(value.strip().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY);
  }

  /** "Tamanho:M" pairs; names and values are short single-line texts, as in the catalog. */
  private static Map<String, List<String>> options(List<String> values) {
    if (values == null) return Map.of();
    if (values.size() > MAX_FILTERS) throw new IllegalArgumentException("Too many options");
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (String value : values) {
      int colon = value.indexOf(':');
      if (colon <= 0 || colon == value.length() - 1) throw new IllegalArgumentException("Option");
      String name = value.substring(0, colon).strip(), choice = value.substring(colon + 1).strip();
      if (invalidText(name) || invalidText(choice)) throw new IllegalArgumentException("Option");
      result.computeIfAbsent(name, key -> new ArrayList<>());
      if (!result.get(name).contains(choice)) result.get(name).add(choice);
    }
    result.replaceAll((name, choices) -> List.copyOf(choices));
    // Insertion order, so the SQL and its bound parameters walk the options the same way.
    return Collections.unmodifiableMap(result);
  }

  private static boolean invalidText(String value) {
    return value.isEmpty()
        || value.length() > 60
        || value.codePoints().anyMatch(code -> Character.isISOControl(code));
  }

  /**
   * The typed search as prefix terms joined by AND ("oculos sol" is {@code oculos:* & sol:*}):
   * accents folded, only letters and digits kept, so nothing typed can change the query syntax.
   */
  static String terms(String text) {
    if (text == null || text.isBlank()) return null;
    if (text.length() > 100) throw new IllegalArgumentException("Search too long");
    String folded =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .strip();
    if (folded.isEmpty()) return null;
    List<String> terms = new ArrayList<>();
    for (String term : folded.split(" "))
      if (terms.size() < MAX_TERMS && !terms.contains(term + ":*")) terms.add(term + ":*");
    return String.join(" & ", terms);
  }

  private static int page(String value) {
    if (value == null || value.isBlank()) return 1;
    if (!value.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Page");
    int page = Integer.parseInt(value);
    if (page < 1 || page > MAX_PAGE) throw new IllegalArgumentException("Page");
    return page;
  }
}
