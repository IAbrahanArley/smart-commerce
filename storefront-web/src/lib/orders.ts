import "server-only";
import { notFound } from "next/navigation";
import type { CartLine } from "@/lib/cart-cookie";
import type { CheckoutPayload } from "@/lib/checkout-input";
import { signedClientIp } from "@/lib/client-ip";
import { validOrderId, validOrderToken } from "@/lib/order-cookie";
import { checkoutPage } from "@/lib/payment-url";
import { apiOrigin, slugPath, unavailable } from "@/lib/public-store";

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "EXPIRED" | "CANCELLED";
export type OrderView = {
  id: string;
  number: number;
  status: OrderStatus;
  items: { productName: string; options: { name: string; value: string }[]; unitPrice: string; quantity: number; lineTotal: string }[];
  subtotal: string;
  shipping: string;
  total: string;
  fulfillment: { type: "PICKUP" | "FIXED_SHIPPING"; text: string };
  reservationExpiresAt: string;
  // Pending and the store accepts payments now (SC-013): the Checkout Pro redirect is offered.
  payable: boolean;
};
export type CheckoutConflict = "PRICE_CHANGED" | "INSUFFICIENT_STOCK" | "ITEM_UNAVAILABLE" | "FULFILLMENT_UNAVAILABLE" | "REQUEST_CONFLICT" | "PAYMENT_UNAVAILABLE";
export type CheckoutResult =
  | { kind: "created"; order: OrderView; token: string }
  | { kind: "replayed"; order: OrderView }
  | { kind: "conflict"; code: CheckoutConflict }
  | { kind: "limited" }
  | { kind: "invalid" }
  | { kind: "missing" }
  // Network failure, timeout or unexpected answer: the order may or may not exist. Retry with the same key.
  | { kind: "ambiguous" };

const MONEY = /^\d{1,20}\.\d{2}$/;
const CONFLICTS: CheckoutConflict[] = ["PRICE_CHANGED", "INSUFFICIENT_STOCK", "ITEM_UNAVAILABLE", "FULFILLMENT_UNAVAILABLE", "REQUEST_CONFLICT", "PAYMENT_UNAVAILABLE"];
const STATUSES: OrderStatus[] = ["PENDING_PAYMENT", "PAID", "EXPIRED", "CANCELLED"];

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function text(value: unknown, max: number): string | null {
  return typeof value === "string" && value.trim() && [...value].length <= max ? value : null;
}

// Projects only the public order fields, even if the upstream adds others.
export function parseOrder(raw: unknown): OrderView | null {
  const order = record(raw);
  const fulfillment = record(order?.fulfillment);
  if (!order || !fulfillment || !validOrderId(order.id) || typeof order.number !== "number" || !Number.isSafeInteger(order.number) || order.number < 1
    || !STATUSES.includes(order.status as OrderStatus) || order.currency !== "BRL" || !Array.isArray(order.items) || order.items.length < 1 || order.items.length > 20
    || ![order.subtotal, order.shipping, order.total].every(value => typeof value === "string" && MONEY.test(value))
    || (fulfillment.type !== "PICKUP" && fulfillment.type !== "FIXED_SHIPPING") || !text(fulfillment.text, 500)
    || typeof order.reservationExpiresAt !== "string" || Number.isNaN(Date.parse(order.reservationExpiresAt))) return null;
  const items: OrderView["items"] = [];
  for (const value of order.items) {
    const item = record(value);
    if (!item || !text(item.productName, 120) || typeof item.unitPrice !== "string" || !MONEY.test(item.unitPrice) || typeof item.lineTotal !== "string" || !MONEY.test(item.lineTotal)
      || typeof item.quantity !== "number" || !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 10 || !Array.isArray(item.options) || item.options.length > 3) return null;
    const options: { name: string; value: string }[] = [];
    for (const raw of item.options) {
      const option = record(raw);
      if (!option || !text(option.name, 40) || !text(option.value, 40)) return null;
      options.push({ name: option.name as string, value: option.value as string });
    }
    items.push({ productName: item.productName as string, options, unitPrice: item.unitPrice, quantity: item.quantity, lineTotal: item.lineTotal });
  }
  return {
    id: (order.id as string).toLowerCase(), number: order.number, status: order.status as OrderStatus, items,
    subtotal: order.subtotal as string, shipping: order.shipping as string, total: order.total as string,
    fulfillment: { type: fulfillment.type, text: fulfillment.text as string }, reservationExpiresAt: order.reservationExpiresAt,
    payable: order.status === "PENDING_PAYMENT" && order.payable === true,
  };
}

export async function createOrder(slug: string, lines: CartLine[], payload: CheckoutPayload, clientIp: string | null): Promise<CheckoutResult> {
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${slugPath(slug)}/orders`, {
      method: "POST",
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      // Only these headers: the visitor's cookies and Authorization are never forwarded.
      headers: { Accept: "application/json", "Content-Type": "application/json", ...signedClientIp(clientIp, process.env.STOREFRONT_FORWARDING_SECRET, Date.now()) },
      body: JSON.stringify({ requestId: payload.requestId, items: lines.map(({ variantId, quantity }) => ({ variantId, quantity })), fulfillment: payload.fulfillment, buyer: payload.buyer, expectedTotal: payload.expectedTotal }),
      signal: AbortSignal.timeout(10000),
    });
  } catch {
    return { kind: "ambiguous" };
  }
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (response.status === 201 || response.status === 200) {
    const result = record(body);
    const order = parseOrder(result?.order);
    if (!order) return { kind: "ambiguous" };
    if (response.status === 200 && result?.replayed === true) return { kind: "replayed", order };
    return validOrderToken(result?.accessToken) ? { kind: "created", order, token: result.accessToken } : { kind: "ambiguous" };
  }
  if (response.status === 409) {
    const code = record(body)?.code;
    return CONFLICTS.includes(code as CheckoutConflict) ? { kind: "conflict", code: code as CheckoutConflict } : { kind: "ambiguous" };
  }
  if (response.status === 429) return { kind: "limited" };
  if (response.status === 400 || response.status === 413) return { kind: "invalid" };
  if (response.status === 404) return { kind: "missing" };
  return { kind: "ambiguous" };
}

export async function loadOrder(slug: string, orderId: string, token: string): Promise<OrderView> {
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${slugPath(slug)}/orders/${encodeURIComponent(orderId)}`, {
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      headers: { Accept: "application/json", "X-Order-Token": token },
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
  const order = parseOrder(raw);
  if (!order || order.id !== orderId.toLowerCase()) unavailable();
  return order;
}

export type PaymentStart =
  | { kind: "redirect"; url: string }
  // The order no longer awaits payment (expired or cancelled).
  | { kind: "closed" }
  // The store cannot take payments now (not connected, or no integration in this environment).
  | { kind: "unavailable" }
  // Provider failure, timeout or unexpected answer: the order stays pending and the buyer may retry.
  | { kind: "failed" };

// Asks the backend for the Checkout Pro page of this order. Idempotent: a retry returns the same page.
export async function startPayment(slug: string, orderId: string, token: string): Promise<PaymentStart> {
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${slugPath(slug)}/orders/${encodeURIComponent(orderId)}/payment`, {
      method: "POST",
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      headers: { Accept: "application/json", "X-Order-Token": token },
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    return { kind: "failed" };
  }
  if (response.status === 404) notFound();
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (response.status === 200) {
    const url = record(body)?.checkoutUrl;
    return checkoutPage(url) ? { kind: "redirect", url } : { kind: "failed" };
  }
  if (response.status === 409) {
    const code = record(body)?.code;
    if (code === "NOT_PAYABLE") return { kind: "closed" };
    if (code === "PAYMENT_UNAVAILABLE") return { kind: "unavailable" };
  }
  return { kind: "failed" };
}
