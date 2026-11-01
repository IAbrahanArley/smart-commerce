package com.smartcommerce.billing.domain;

/**
 * SaaS plans approved on 2026-10-26 (PRICING_PROPOSAL.md, ADR-0017 B9). Limits apply per owner
 * account: companies (one store each, ADR-0006), products per store and assistant questions per
 * month summed over every store of the owner, and store employees (STAFF, ADR-0019 C1, limits
 * decided on 2026-11-01) counted once per person over every store of the owner. Prices live in
 * Stripe (SC-032), not here.
 */
public enum Plan {
  ESSENCIAL(1, 150, 0, 1),
  PROFISSIONAL(2, 2_000, 60, 3),
  /** "Produtos sem limite" on the landing; 20 000 is a technical ceiling, not a commercial one. */
  ESCALA(5, 20_000, 200, 10);

  private final int companies;
  private final int productsPerStore;
  private final int monthlyQuestions;
  private final int staff;

  Plan(int companies, int productsPerStore, int monthlyQuestions, int staff) {
    this.companies = companies;
    this.productsPerStore = productsPerStore;
    this.monthlyQuestions = monthlyQuestions;
    this.staff = staff;
  }

  public int companies() {
    return companies;
  }

  public int productsPerStore() {
    return productsPerStore;
  }

  public int monthlyQuestions() {
    return monthlyQuestions;
  }

  public int staff() {
    return staff;
  }
}
