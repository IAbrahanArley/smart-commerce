// Buyer-facing order as the storefront shows it (SC-008, SC-046). Pure: shared by the server-only
// API client, the "Acompanhar pedido" form state and the smoke test.

import { validOrderId } from "./order-cookie.ts";

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "REFUNDED" | "EXPIRED" | "CANCELLED";
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
const MONEY = /^\d{1,20}\.\d{2}$/;
const STATUSES: OrderStatus[] = ["PENDING_PAYMENT", "PAID", "REFUNDED", "EXPIRED", "CANCELLED"];

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

