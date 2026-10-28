// Sprint 8 (SC-046, ADR-0018 V10): "Acompanhar pedido". Pure helpers for the smoke test. The
// backend normalizes again, limits attempts and answers one 404 for any mismatch.

import type { OrderView } from "./orders-view.ts";

export type LookupField = "number" | "email";
export type LookupValues = Record<LookupField, string>;
export type LookupState = {
  values: LookupValues;
  errors: Partial<Record<LookupField, string>>;
  message: string | null;
  order: OrderView | null;
  attempt: number;
};

export const EMPTY_LOOKUP: LookupState = { values: { number: "", email: "" }, errors: {}, message: null, order: null, attempt: 0 };

const EMAIL = /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/;

/** "123", "#123" or "nº 123" become 123; null when it is not an order number. */
export function orderNumber(raw: string): number | null {
  const digits = raw.trim().replace(/^(?:n[º°o.]?|#)\s*/i, "");
  return /^[1-9]\d{0,11}$/.test(digits) ? Number(digits) : null;
}

export function validateLookup(values: LookupValues): { number: number; email: string } | { errors: LookupState["errors"] } {
  const errors: LookupState["errors"] = {};
  const number = orderNumber(values.number);
  if (number === null) errors.number = "Informe o número do pedido, por exemplo 1024.";
  const email = values.email.trim();
  if (email.length > 254 || !EMAIL.test(email)) errors.email = "Informe o e-mail usado na compra, por exemplo nome@exemplo.com.";
  return number === null || errors.email ? { errors } : { number, email };
}
