import "server-only";
import { notFound } from "next/navigation";
import type { CartLine } from "@/lib/cart-cookie";
import { apiOrigin, slugPath, unavailable } from "@/lib/public-store";

export type QuoteStatus = "AVAILABLE" | "INSUFFICIENT" | "UNAVAILABLE";
export type QuoteLine = {
  variantId: string;
  quantity: number;
  status: QuoteStatus;
  productId: string | null;
  productName: string | null;
  options: { name: string; value: string }[];
  unitPrice: string | null;
  lineTotal: string | null;
};
export type DeliveryOption = { type: "PICKUP"; text: string; fee: string } | { type: "FIXED_SHIPPING"; text: string; fee: string };
export type CartQuote = { lines: QuoteLine[]; subtotal: string; purchasable: boolean; delivery: DeliveryOption[] };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MONEY = /^\d{1,20}\.\d{2}$/;
const STATUSES: QuoteStatus[] = ["AVAILABLE", "INSUFFICIENT", "UNAVAILABLE"];

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) unavailable();
  return value as Record<string, unknown>;
}

function label(value: unknown, max: number): string {
  if (typeof value !== "string" || !value.trim() || [...value].length > max) unavailable();
  return value;
}

// Prices and states come only from the backend; the cookie never carries amounts.
export async function quoteCart(slug: string, lines: CartLine[]): Promise<CartQuote> {
  const path = `${slugPath(slug)}/cart/quote`;
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${path}`, {
      method: "POST",
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      // Only these headers: the visitor's cookies and Authorization are never forwarded.
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ items: lines.map(({ variantId, quantity }) => ({ variantId, quantity })) }),
      signal: AbortSignal.timeout(5000),
    });
  } catch {
    return unavailable();
  }
  if (response.status === 404) notFound();
  if (!response.ok) unavailable();
  let raw: unknown;
  try {
    raw = await response.json();
  } catch {
    return unavailable();
  }
  const input = object(raw);
  if (input.currency !== "BRL" || typeof input.subtotal !== "string" || !MONEY.test(input.subtotal)
    || typeof input.purchasable !== "boolean" || !Array.isArray(input.lines) || input.lines.length !== lines.length) unavailable();
  const quoted = input.lines.map((value, index): QuoteLine => {
    const line = object(value);
    const status = line.status as QuoteStatus;
    if (typeof line.variantId !== "string" || line.variantId.toLowerCase() !== lines[index].variantId) unavailable();
    if (line.quantity !== lines[index].quantity || !STATUSES.includes(status)) unavailable();
    const base = { variantId: lines[index].variantId, quantity: lines[index].quantity, status };
    if (status === "UNAVAILABLE") return { ...base, productId: null, productName: null, options: [], unitPrice: null, lineTotal: null };
    if (typeof line.productId !== "string" || !UUID.test(line.productId) || typeof line.unitPrice !== "string" || !MONEY.test(line.unitPrice)
      || typeof line.lineTotal !== "string" || !MONEY.test(line.lineTotal) || !Array.isArray(line.options) || line.options.length > 3) unavailable();
    const options = line.options.map(option => {
      const item = object(option);
      return { name: label(item.name, 40), value: label(item.value, 40) };
    });
    // Project only public fields even if the upstream adds private properties.
    return { ...base, productId: line.productId, productName: label(line.productName, 120), options, unitPrice: line.unitPrice, lineTotal: line.lineTotal };
  });
  return { lines: quoted, subtotal: input.subtotal, purchasable: input.purchasable, delivery: delivery(input.delivery) };
}

// Enabled store options (SC-006/SC-008). Pickup is free; the fixed fee and texts come from the store.
function delivery(value: unknown): DeliveryOption[] {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > 2) unavailable();
  const options = value.map((raw): DeliveryOption => {
    const option = object(raw);
    if (option.type === "PICKUP") return { type: "PICKUP", text: label(option.instructions, 500), fee: "0.00" };
    if (option.type === "FIXED_SHIPPING" && option.currency === "BRL" && typeof option.amount === "string" && MONEY.test(option.amount))
      return { type: "FIXED_SHIPPING", text: label(option.area, 500), fee: option.amount };
    return unavailable();
  });
  if (new Set(options.map(option => option.type)).size !== options.length) unavailable();
  return options;
}
