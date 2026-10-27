// Brands of a store and the "Vitrine" card of a product (Sprint 8, SC-041). Pure helpers, free of
// React and Next imports for the smoke test. The backend validates again.

import { decimalInput } from "./catalog.ts";

export type Brand = { id: string; name: string; slug: string; products: number };

export const MAX_BRANDS = 200;

export function parseBrands(raw: unknown): Brand[] | null {
  if (!Array.isArray(raw)) return null;
  const list: Brand[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") return null;
    const value = item as Record<string, unknown>;
    if (typeof value.id !== "string" || typeof value.name !== "string" || typeof value.slug !== "string" || typeof value.products !== "number") return null;
    list.push({ id: value.id, name: value.name, slug: value.slug, products: value.products });
  }
  return list;
}

/** The message for a failed brand request. */
export function brandError(status: number, code?: string): string {
  if (status === 409 && code === "NAME_CONFLICT") return "Já existe uma marca com esse nome na loja.";
  if (status === 409 && code === "LIMIT") return `A loja já tem ${MAX_BRANDS} marcas. Remova uma antes de criar outra.`;
  if (status === 400) return "Informe um nome de marca com até 60 caracteres.";
  if (status === 404) return "Marca ou loja indisponível. Recarregue a lista.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível concluir. Tente novamente.";
}

/**
 * A "compare at" price typed by the owner: empty clears it; otherwise a BRL amount greater than the
 * variant's price. Returns the canonical decimal or an error message.
 */
export function compareAtInput(raw: string, price: string): { value: string | null } | { error: string } {
  if (!raw.trim()) return { value: null };
  const value = decimalInput(raw);
  if (!value || /^0\.00$/.test(value)) return { error: "Use um valor em reais, por exemplo 249,90." };
  const [whole, cents] = value.split("."), [priceWhole, priceCents] = price.split(".");
  const above = BigInt(whole + cents) > BigInt(priceWhole + priceCents.padEnd(2, "0"));
  return above ? { value } : { error: "O preço “de” precisa ser maior que o preço atual." };
}

/** The message for a failed "Vitrine" card request. */
export function storefrontError(status: number, code?: string): string {
  if (status === 409 && code === "SLUG_CONFLICT") return "Outro produto da loja já usa esse endereço.";
  if (status === 400 && code === "COMPARE_AT_NOT_ABOVE") return "O preço “de” precisa ser maior que o preço atual.";
  if (status === 400) return "Confira o endereço (letras sem acento, números e hífens) e os preços em reais.";
  if (status === 404) return "Produto ou marca indisponível. Recarregue o produto.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  return "Não foi possível salvar. Tente novamente.";
}
