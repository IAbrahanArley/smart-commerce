package com.smartcommerce.storefront.domain;

/**
 * Orders of the storefront catalog (SC-042, ADR-0018 V6). Every order ends with the product id, so
 * pages never repeat or skip a product when values tie.
 */
public enum CatalogSort {
  /** Search rank; only with a search, otherwise the catalog falls back to the most recent. */
  RELEVANCE("relevancia"),
  RECENT("recentes"),
  /** Units sold in paid orders in the last 90 days (ADR-0018 V7). */
  BEST_SELLING("mais-vendidos"),
  PRICE_ASC("menor-preco"),
  PRICE_DESC("maior-preco"),
  DISCOUNT("maior-desconto");

  private final String parameter;

  CatalogSort(String parameter) {
    this.parameter = parameter;
  }

  public String parameter() {
    return parameter;
  }

  public static CatalogSort fromParameter(String value) {
    for (CatalogSort sort : values()) if (sort.parameter.equals(value)) return sort;
    throw new IllegalArgumentException("Sort");
  }
}
