export type VariantOption = { name: string; value: string };
export type ProductVariant = { id: string; sku: string; price: string; cost: string | null; currency: "BRL"; version: number; options: VariantOption[] };
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
};

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
