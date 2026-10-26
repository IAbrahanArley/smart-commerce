// Product list filters (design system phase 3). Pure, imported by the smoke test. The whole catalog is
// already loaded, so filtering and sorting happen here; prices are compared as exact cents, never as
// floating point.

import type { Product } from "@/lib/catalog";

export type Visibility = "all" | "visible" | "hidden";
export type ProductSort = "default" | "name" | "price-asc" | "price-desc";
export type ProductFilters = { query: string; category: string; visibility: Visibility; sort: ProductSort };

export const EMPTY_FILTERS: ProductFilters = { query: "", category: "", visibility: "all", sort: "default" };
/** Category filter value for products without a category. */
export const NO_CATEGORY = "__none__";

function fold(text: string): string {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
}

const ZERO = BigInt(0);
const HUNDRED = BigInt(100);

function cents(price: string): bigint {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(price);
  return match ? BigInt(match[1]) * HUNDRED + BigInt((match[2] ?? "0").padEnd(2, "0")) : ZERO;
}

type Filterable = Pick<Product, "name" | "sku" | "price" | "published" | "categoryId"> & { variants: { sku: string }[] };

export function filterProducts<T extends Filterable>(products: T[], filters: ProductFilters): T[] {
  const words = fold(filters.query).split(/\s+/).filter(Boolean);
  const result = products.filter(product => {
    if (filters.visibility === "visible" && !product.published) return false;
    if (filters.visibility === "hidden" && product.published) return false;
    if (filters.category === NO_CATEGORY ? product.categoryId !== null : filters.category && product.categoryId !== filters.category) return false;
    if (!words.length) return true;
    const text = fold([product.name, product.sku, ...product.variants.map(variant => variant.sku)].join(" "));
    return words.every(word => text.includes(word));
  });
  if (filters.sort === "name") return [...result].sort((a, b) => a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" }));
  if (filters.sort === "price-asc" || filters.sort === "price-desc") {
    const direction = filters.sort === "price-asc" ? 1 : -1;
    return [...result].sort((a, b) => { const difference = cents(a.price) - cents(b.price); return difference === ZERO ? 0 : (difference > ZERO ? 1 : -1) * direction; });
  }
  return result;
}

export function filtersActive(filters: ProductFilters): boolean {
  return filters.query.trim() !== "" || filters.category !== "" || filters.visibility !== "all" || filters.sort !== "default";
}

export function resultText(shown: number, total: number): string {
  const format = (value: number) => new Intl.NumberFormat("pt-BR").format(value);
  if (shown === total) return total === 1 ? "1 produto" : `${format(total)} produtos`;
  return `${format(shown)} de ${format(total)} produtos`;
}
