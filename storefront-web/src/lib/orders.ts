import "server-only";
import { notFound } from "next/navigation";
import type { CartLine } from "@/lib/cart-cookie";
import type { CheckoutPayload } from "@/lib/checkout-input";
import { signedClientIp } from "@/lib/client-ip";
import { validOrderToken } from "@/lib/order-cookie";
import { type OrderView, parseOrder } from "@/lib/orders-view";
import { checkoutPage } from "@/lib/payment-url";
import { apiOrigin, slugPath, unavailable } from "@/lib/public-store";

export type { OrderStatus, OrderView } from "@/lib/orders-view";

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

const CONFLICTS: CheckoutConflict[] = ["PRICE_CHANGED", "INSUFFICIENT_STOCK", "ITEM_UNAVAILABLE", "FULFILLMENT_UNAVAILABLE", "REQUEST_CONFLICT", "PAYMENT_UNAVAILABLE"];

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
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

export type LookupResult = { kind: "found"; order: OrderView } | { kind: "missing" } | { kind: "limited" } | { kind: "invalid" } | { kind: "unavailable" };

/**
 * SC-046: "Acompanhar pedido". Number and e-mail go in the body, never in a URL. The signed client
 * address lets the backend limit attempts per visitor; any mismatch is the backend's single 404.
 */
export async function lookupOrder(slug: string, number: number, email: string, clientIp: string | null): Promise<LookupResult> {
  let response: Response;
  try {
    response = await fetch(`${apiOrigin()}${slugPath(slug)}/orders/lookup`, {
      method: "POST",
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      headers: { Accept: "application/json", "Content-Type": "application/json", ...signedClientIp(clientIp, process.env.STOREFRONT_FORWARDING_SECRET, Date.now()) },
      body: JSON.stringify({ number: String(number), email }),
      signal: AbortSignal.timeout(5000),
    });
  } catch {
    return { kind: "unavailable" };
  }
  if (response.status === 404) return { kind: "missing" };
  if (response.status === 429) return { kind: "limited" };
  if (response.status === 400) return { kind: "invalid" };
  if (!response.ok) return { kind: "unavailable" };
  try {
    const order = parseOrder(await response.json());
    // Paying needs the token of the browser that placed the order; this view never offers it.
    return order && order.number === number ? { kind: "found", order: { ...order, payable: false } } : { kind: "unavailable" };
  } catch {
    return { kind: "unavailable" };
  }
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
