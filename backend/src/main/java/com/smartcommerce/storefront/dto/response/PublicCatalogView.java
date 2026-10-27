package com.smartcommerce.storefront.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * One page of the storefront catalog (Sprint 8, SC-042): the store, its category menu, the category
 * being browsed with its path, the cards of this page and the facets of the current search. Prices
 * are decimal strings in BRL; nothing private (cost, SKU, exact stock, internal ids of the store)
 * is exposed.
 *
 * @param category the category being browsed, or {@code null} for the whole catalog
 * @param search the search as typed, or {@code null}
 * @param sort the order applied, as its query parameter
 * @param maxInstallments card installments the store offers in Mercado Pago ("em até N×"), or
 *     {@code null}; interest depends on the seller's account, so no installment value is given
 */
public record PublicCatalogView(
    PublicStoreView store,
    List<MenuItem> menu,
    CategoryPath category,
    String search,
    String sort,
    int page,
    int pageSize,
    long total,
    List<Card> products,
    Facets facets,
    Integer maxInstallments) {

  /** A link to a category or brand. */
  public record Link(String name, String slug) {}

  /** A menu entry with its subcategories; only categories with published products appear. */
  public record MenuItem(String name, String slug, List<MenuItem> children) {}

  /** The category being browsed and its parents, root first (itself last). */
  public record CategoryPath(String name, String slug, List<Link> path) {}

  /**
   * A product card. {@code compareAtPrice} and {@code discountPercent} are present only when the
   * "compare at" price of the cheapest variant is above its price; the percent is rounded down.
   */
  public record Card(
      UUID id,
      String slug,
      String name,
      String brand,
      String price,
      String compareAtPrice,
      Integer discountPercent,
      String currency,
      boolean available) {}

  public record Facets(
      List<Count> categories, List<Count> brands, List<PriceRange> prices, List<Option> options) {}

  /** A category (direct subcategories of the one browsed) or brand, with its product count. */
  public record Count(String slug, String name, long count) {}

  /**
   * Products priced from {@code min} (inclusive) to below {@code max}; {@code max} null: no cap.
   */
  public record PriceRange(String min, String max, long count) {}

  public record Option(String name, List<OptionValue> values) {}

  public record OptionValue(String value, long count) {}
}
