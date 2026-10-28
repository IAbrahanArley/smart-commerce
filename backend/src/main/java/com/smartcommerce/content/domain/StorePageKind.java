package com.smartcommerce.content.domain;

/**
 * Institutional pages a store may write (SC-043, ADR-0018 V8): a fixed list, so the footer, the
 * addresses and the titles stay predictable for buyers.
 */
public enum StorePageKind {
  ABOUT("sobre", "Sobre a loja"),
  RETURNS("trocas-e-devolucoes", "Trocas e devoluções"),
  SHIPPING("entrega", "Entrega"),
  PRIVACY("privacidade", "Privacidade"),
  TERMS("termos", "Termos de uso");

  private final String slug;
  private final String title;

  StorePageKind(String slug, String title) {
    this.slug = slug;
    this.title = title;
  }

  public String slug() {
    return slug;
  }

  public String title() {
    return title;
  }

  /** The kind from its storefront address; unknown addresses raise IllegalArgumentException. */
  public static StorePageKind fromSlug(String slug) {
    for (StorePageKind kind : values()) if (kind.slug.equals(slug)) return kind;
    throw new IllegalArgumentException("Unknown page");
  }
}
