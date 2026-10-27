export type VariantOption = { name: string; value: string };
export type ProductVariant = {
  id: string; sku: string; price: string; cost: string | null; currency: "BRL"; version: number; options: VariantOption[];
  /** SC-041: display-only "compare at" price; absent in older responses. */
  compareAtPrice?: string | null;
};
export type VariantDraft = { sku: string; price: string; cost: string; options: VariantOption[] };
export type Product = {
  id: string;
  name: string;
  sku: string;
  price: string;
  cost: string | null;
  currency: "BRL";
  version: number;
  published: boolean;
  descriptionHtml: string;
  kind: "SIMPLE" | "VARIABLE";
  variants: ProductVariant[];
  coverImageId: string | null;
  coverImageAlt: string | null;
  imageCount: number;
  /** MVP block 1c: null when the product has no category. */
  categoryId: string | null;
  /** SC-041: brand and storefront address; absent in older responses. */
  brandId?: string | null;
  slug?: string | null;
};

export const MAX_PRODUCT_PHOTOS = 6;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Private (session) URL of a product's cover, or null without a valid cover id. Same origin: the
// admin proxies /api to the backend, which checks the OWNER before serving the bytes.
export function coverUrl(tenantId: string, product: Pick<Product, "id" | "coverImageId">): string | null {
  if (!product.coverImageId || !UUID.test(product.coverImageId) || !UUID.test(product.id)) return null;
  return `/api/tenants/${encodeURIComponent(tenantId)}/products/${encodeURIComponent(product.id)}/images/${encodeURIComponent(product.coverImageId)}/content`;
}

export function photoCountText(count: number): string {
  if (!Number.isSafeInteger(count) || count <= 0) return "Sem foto";
  return `${count} de ${MAX_PRODUCT_PHOTOS} fotos`;
}

// Decimal text is canonicalized without floating-point conversion or rounding.
export function decimalInput(value: string): string | null {
  const trimmed = value.trim();
  if (!/^\d{1,17}(?:[.,]\d{1,2})?$/.test(trimmed)) return null;
  const [whole, fraction = ""] = trimmed.replace(",", ".").split(".");
  return `${whole.replace(/^0+(?=\d)/, "")}.${fraction.padEnd(2, "0")}`;
}

// Signed amounts (margins, deductions) keep their minus sign: "-R$ 25,00". The sign is applied to
// the formatted absolute value, so "-0.50" never loses it to BigInt(-0).
export function formatBRL(value: string): string {
  if (!/^-?\d+\.\d{2}$/.test(value)) return "Valor indisponível";
  const negative = value.startsWith("-");
  const [whole, fraction] = (negative ? value.slice(1) : value).split(".");
  const formatted = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
    .formatToParts(BigInt(whole))
    .map(part => part.type === "fraction" ? fraction : part.value)
    .join("");
  return negative && /[1-9]/.test(whole + fraction) ? `-${formatted}` : formatted;
}
