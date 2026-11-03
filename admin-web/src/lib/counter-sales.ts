// SC-053 (ADR-0019 C6, C8): sales at the physical store. Pure helpers, free of React and Next
// imports for the smoke test. The backend decides prices, stock and limits; this only previews.

export type PaymentMethod = "CASH" | "PIX" | "DEBIT" | "CREDIT";
export type CounterProduct = { variantId: string; name: string; options: string; sku: string; price: string; available: number };
export type CounterItem = { variantId: string; name: string; options: string; sku: string; unitPrice: string; quantity: number; lineTotal: string };
export type CounterSale = {
  id: string; paymentMethod: PaymentMethod; subtotal: string; discount: string; total: string; fee: string | null;
  accountName: string | null; expectedReleaseOn: string | null; status: "ACTIVE" | "CANCELLED"; createdBy: string; createdAt: string;
  cancelledBy: string | null; cancelledAt: string | null; cancelReason: string | null; restocked: boolean | null; items: CounterItem[];
  // SC-054: the day an imported sale happened (null for sales registered live).
  soldOn: string | null;
};
export type CounterOverview = { role: "OWNER" | "STAFF"; staffDiscountLimit: string; pixAccountName: string | null; sessions: { id: string; accountName: string }[]; sales: CounterSale[] };
export type CounterSettings = { staffDiscountLimit: string; pixAccountId: string | null; debitFeePercent: string | null; debitReleaseDays: number | null; creditFeePercent: string | null; creditReleaseDays: number | null };
export type CartLine = { product: CounterProduct; quantity: number };

export const METHOD_LABELS: Record<PaymentMethod, string> = { CASH: "Dinheiro", PIX: "Pix", DEBIT: "Cartão de débito", CREDIT: "Cartão de crédito" };
export const METHODS = Object.keys(METHOD_LABELS) as PaymentMethod[];

const cents = (amount: string) => BigInt(amount.replace(".", ""));
const ZERO = BigInt(0); const HUNDRED = BigInt(100);
const reais = (value: bigint) => { const sign = value < ZERO ? "-" : ""; const abs = value < ZERO ? -value : value; return `${sign}${abs / HUNDRED}.${String(abs % HUNDRED).padStart(2, "0")}`; };

// Preview only, like the backend: price × quantity per line, summed.
export function subtotal(lines: CartLine[]): string {
  return reais(lines.reduce((sum, line) => sum + cents(line.product.price) * BigInt(line.quantity), ZERO));
}

export function minus(total: string, discount: string): string {
  return reais(cents(total) - cents(discount));
}

// The employee's discount must stay within the OWNER's percentage of the subtotal.
export function withinLimit(discount: string, subtotalAmount: string, limitPercent: string): boolean {
  return cents(discount) * BigInt(10000) <= cents(subtotalAmount) * cents(limitPercent);
}

// A percentage as typed ("2,5", "10") as the API's "2.50"; blank stays blank; errors are messages.
export function parsePercent(value: string, max: number, inclusive: boolean): { percent: string | null } | { error: string } {
  const text = value.trim().replace(",", ".").replace(/%$/, "").trim();
  if (!text) return { percent: null };
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(text)) return { error: "Use um número com até duas casas decimais." };
  const number = Number(text);
  if (inclusive ? number > max : number >= max) return { error: inclusive ? `Use de 0 a ${max}.` : `Use um valor menor que ${max}.` };
  const [whole, fraction = ""] = text.split(".");
  return { percent: `${Number(whole)}.${fraction.padEnd(2, "0")}` };
}

export function parseDays(value: string): { days: number | null } | { error: string } {
  const text = value.trim();
  if (!text) return { days: null };
  if (!/^\d{1,3}$/.test(text) || Number(text) > 120) return { error: "Use de 0 a 120 dias, ou deixe em branco." };
  return { days: Number(text) };
}

export function itemLabel(item: { name: string; options: string }): string {
  return item.options ? `${item.name} (${item.options})` : item.name;
}
