// Sprint 8 (SC-045): the storefront catalog (SC-042 API) as the pages use it. Pure helpers, free of
// React and Next imports for the smoke test. The backend validated everything already; this second
// boundary keeps only the public fields in the expected shape and refuses anything else.

import { type MenuItem, parseMenu } from "./menu.ts";

export type Link = { name: string; slug: string };
export type Card = {
  id: string; slug: string | null; name: string; brand: string | null; price: string;
  compareAtPrice: string | null; discountPercent: number | null; available: boolean;
};
export type Count = { slug: string; name: string; count: number };
export type PriceRange = { min: string; max: string | null; count: number };
export type OptionFacet = { name: string; values: { value: string; count: number }[] };
export type CatalogPage = {
  menu: MenuItem[];
  category: { name: string; slug: string; path: Link[] } | null;
  search: string | null; sort: string; page: number; pageSize: number; total: number;
  products: Card[];
  facets: { categories: Count[]; brands: Count[]; prices: PriceRange[]; options: OptionFacet[] };
  maxInstallments: number | null;
};

export const SORTS: { value: string; label: string }[] = [
  { value: "relevancia", label: "Relevância" },
  { value: "recentes", label: "Mais recentes" },
  { value: "mais-vendidos", label: "Mais vendidos" },
  { value: "menor-preco", label: "Menor preço" },
  { value: "maior-preco", label: "Maior preço" },
  { value: "maior-desconto", label: "Maior desconto" },
];

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const MONEY = /^\d{1,17}\.\d{2}$/;

function fail(): never {
  throw new Error("Catálogo em formato inesperado.");
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail();
  return value as Record<string, unknown>;
}

function text(value: unknown, max: number): string {
  if (typeof value !== "string" || !value.trim() || [...value].length > max) fail();
  return value;
}

function slug(value: unknown): string {
  if (typeof value !== "string" || value.length > 80 || !SLUG.test(value)) fail();
  return value;
}

function count(value: unknown): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) fail();
  return value as number;
}

function link(value: unknown): Link {
  const item = record(value);
  return { name: text(item.name, 60), slug: slug(item.slug) };
}

/** A product card of the catalog or of "related products". */
export function parseCard(value: unknown): Card {
  const item = record(value);
  if (typeof item.id !== "string" || !UUID.test(item.id) || typeof item.price !== "string" || !MONEY.test(item.price)
    || item.currency !== "BRL" || typeof item.available !== "boolean") fail();
  const compare = item.compareAtPrice ?? null, percent = item.discountPercent ?? null;
  if (compare !== null && (typeof compare !== "string" || !MONEY.test(compare))) fail();
  if (percent !== null && (!Number.isInteger(percent) || (percent as number) < 1 || (percent as number) > 99)) fail();
  return {
    id: item.id, slug: item.slug == null ? null : slug(item.slug), name: text(item.name, 120),
    brand: item.brand == null ? null : text(item.brand, 60), price: item.price,
    compareAtPrice: compare as string | null, discountPercent: compare === null ? null : percent as number | null,
    available: item.available,
  };
}

export function parseInstallments(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  if (!Number.isInteger(value) || (value as number) < 1 || (value as number) > 12) fail();
  return value as number;
}

export function parseCatalog(raw: unknown): CatalogPage {
  const input = record(raw);
  const facets = record(input.facets);
  if (!Array.isArray(input.products) || input.products.length > 24 || !Array.isArray(facets.categories)
    || !Array.isArray(facets.brands) || !Array.isArray(facets.prices) || !Array.isArray(facets.options)) fail();
  const category = input.category == null ? null : record(input.category);
  return {
    menu: parseMenu(input),
    category: category === null ? null : {
      name: text(category.name, 60), slug: slug(category.slug),
      path: Array.isArray(category.path) && category.path.length <= 3 ? category.path.map(link) : fail(),
    },
    search: input.search == null ? null : text(input.search, 100),
    sort: SORTS.some(sort => sort.value === input.sort) ? input.sort as string : fail(),
    page: count(input.page), pageSize: count(input.pageSize), total: count(input.total),
    products: input.products.map(parseCard),
    facets: {
      categories: facets.categories.map(value => { const item = record(value); return { slug: slug(item.slug), name: text(item.name, 60), count: count(item.count) }; }),
      brands: facets.brands.map(value => { const item = record(value); return { slug: slug(item.slug), name: text(item.name, 60), count: count(item.count) }; }),
      prices: facets.prices.map(value => {
        const item = record(value);
        if (typeof item.min !== "string" || !MONEY.test(item.min) || (item.max !== null && (typeof item.max !== "string" || !MONEY.test(item.max)))) fail();
        return { min: item.min, max: item.max as string | null, count: count(item.count) };
      }),
      options: facets.options.map(value => {
        const item = record(value);
        if (!Array.isArray(item.values)) fail();
        return { name: text(item.name, 60), values: item.values.map(entry => { const choice = record(entry); return { value: text(choice.value, 60), count: count(choice.count) }; }) };
      }),
    },
    maxInstallments: parseInstallments(input.maxInstallments),
  };
}

/** The filters a catalog URL may carry, in the API's own names; anything else is dropped. */
const FILTER_KEYS = ["q", "marca", "preco_min", "preco_max", "opcao", "ordem", "pagina"] as const;

export type SearchParams = Record<string, string | string[] | undefined>;

/** The catalog query for the API from the page URL, keeping only known filters. */
export function apiQuery(params: SearchParams, category?: string): URLSearchParams {
  const query = new URLSearchParams();
  if (category) query.set("categoria", category);
  for (const key of FILTER_KEYS) {
    const value = params[key];
    for (const entry of Array.isArray(value) ? value : value === undefined ? [] : [value])
      if (entry.length <= 120) query.append(key, entry);
  }
  return query;
}

/** The page URL with one filter value toggled (added if absent, removed if present); back to page 1. */
export function toggleHref(base: string, params: SearchParams, key: "marca" | "opcao", value: string): string {
  const query = apiQuery(params);
  const values = query.getAll(key);
  query.delete(key); query.delete("pagina");
  for (const entry of values.includes(value) ? values.filter(entry => entry !== value) : [...values, value]) query.append(key, entry);
  return query.size ? `${base}?${query}` : base;
}

/** The page URL with the price range set, or cleared when it is the one already applied. */
export function priceHref(base: string, params: SearchParams, range: PriceRange): string {
  const query = apiQuery(params);
  const active = query.get("preco_min") === range.min && (query.get("preco_max") ?? null) === (range.max ? centsBelow(range.max) : null);
  query.delete("preco_min"); query.delete("preco_max"); query.delete("pagina");
  if (!active) {
    query.set("preco_min", range.min);
    if (range.max) query.set("preco_max", centsBelow(range.max));
  }
  return query.size ? `${base}?${query}` : base;
}

/** Ranges are "from min to below max": the inclusive upper bound is one cent less. */
export function centsBelow(amount: string): string {
  const cents = BigInt(amount.replace(".", "")) - BigInt(1);
  const digits = cents.toString().padStart(3, "0");
  return `${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/** The page URL of another page number, keeping every filter. */
export function pageHref(base: string, params: SearchParams, page: number): string {
  const query = apiQuery(params);
  query.delete("pagina");
  if (page > 1) query.set("pagina", String(page));
  return query.size ? `${base}?${query}` : base;
}

/** Storefront path of a product: its address, or the original id route for rows without one. */
export function productPath(storeSlug: string, card: { id: string; slug: string | null }): string {
  const store = `/lojas/${encodeURIComponent(storeSlug)}`;
  return card.slug ? `${store}/p/${encodeURIComponent(card.slug)}` : `${store}/produtos/${encodeURIComponent(card.id)}`;
}

/** "em até 6× no Mercado Pago": never "sem juros", interest follows the seller's account (ADR-0018 V4). */
export function installmentsText(max: number | null): string | null {
  return max && max > 1 ? `em até ${max}× no Mercado Pago` : null;
}
