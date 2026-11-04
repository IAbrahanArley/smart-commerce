// Product list filters (design system phase 3, paged in the backend since 2026-11-04). Pure, imported
// by the smoke test. The backend filters, orders, counts and pages; here the filters only travel
// between the screen, the URL (?busca=&categoria=&vitrine=&ordem=&pagina=) and the API query.

import type { Product } from "@/lib/catalog";

export type Visibility = "all" | "visible" | "hidden";
export type ProductSort = "default" | "name" | "price-asc" | "price-desc";
export type ProductFilters = { query: string; category: string; visibility: Visibility; sort: ProductSort; page: number };
/** One page of the admin product list, as the backend returns it. */
export type ProductPage = { products: Product[]; page: number; pageSize: number; matching: number; catalogTotal: number; publishedTotal: number };

export const EMPTY_FILTERS: ProductFilters = { query: "", category: "", visibility: "all", sort: "default", page: 1 };
/** Category filter value for products without a category. */
export const NO_CATEGORY = "__none__";

/** Query string of the products API for these filters (the backend's own parameter names). */
export function productsApiQuery(filters: ProductFilters): string {
  const query = new URLSearchParams();
  if (filters.query.trim()) query.set("q", filters.query.trim());
  if (filters.category) query.set("category", filters.category === NO_CATEGORY ? "none" : filters.category);
  if (filters.visibility !== "all") query.set("visibility", filters.visibility);
  if (filters.sort !== "default") query.set("sort", filters.sort);
  if (filters.page > 1) query.set("page", String(filters.page));
  const text = query.toString();
  return text ? `?${text}` : "";
}

/** How many pages hold {@code matching} products, at least one. */
export function pageCount(matching: number, pageSize: number): number {
  return Math.max(1, Math.ceil(matching / pageSize));
}

export function filtersActive(filters: ProductFilters): boolean {
  return filters.query.trim() !== "" || filters.category !== "" || filters.visibility !== "all" || filters.sort !== "default";
}

export function resultText(shown: number, total: number): string {
  const format = (value: number) => new Intl.NumberFormat("pt-BR").format(value);
  if (shown === total) return total === 1 ? "1 produto" : `${format(total)} produtos`;
  return `${format(shown)} de ${format(total)} produtos`;
}

const VISIBILITIES: Visibility[] = ["all", "visible", "hidden"];
const SORTS: ProductSort[] = ["default", "name", "price-asc", "price-desc"];
type Query = { busca?: string; categoria?: string; vitrine?: string; ordem?: string; pagina?: string };

/** Filters from the URL (?busca=&categoria=&vitrine=&ordem=&pagina=); anything unexpected falls back to the default. */
export function filtersFromQuery(query: Query): ProductFilters {
  const category = query.categoria ?? "";
  return {
    query: (query.busca ?? "").slice(0, 200),
    category: category === NO_CATEGORY || /^[0-9a-f-]{36}$/i.test(category) ? category : "",
    visibility: VISIBILITIES.includes(query.vitrine as Visibility) ? query.vitrine as Visibility : "all",
    sort: SORTS.includes(query.ordem as ProductSort) ? query.ordem as ProductSort : "default",
    page: /^[1-9][0-9]{0,4}$/.test(query.pagina ?? "") ? Number(query.pagina) : 1,
  };
}

/** Query string that reflects the filters, empty for the defaults, so the list can be reloaded or shared. */
export function filtersQuery(filters: ProductFilters): string {
  const query = new URLSearchParams();
  if (filters.query.trim()) query.set("busca", filters.query.trim());
  if (filters.category) query.set("categoria", filters.category);
  if (filters.visibility !== "all") query.set("vitrine", filters.visibility);
  if (filters.sort !== "default") query.set("ordem", filters.sort);
  if (filters.page > 1) query.set("pagina", String(filters.page));
  const text = query.toString();
  return text ? `?${text}` : "";
}
