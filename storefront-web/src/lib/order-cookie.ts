// Buyer access token storage (ADR-0012 §3). The backend returns the token once; it lives only in an
// HttpOnly cookie scoped to the order's own status path. Free of Next imports for the smoke test.

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TOKEN = /^[A-Za-z0-9_-]{43}$/;
const ORDER_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

export function validOrderId(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

export function validOrderToken(value: unknown): value is string {
  return typeof value === "string" && TOKEN.test(value);
}

export function orderPath(slug: string, orderId: string): string {
  return `/lojas/${slug}/pedidos/${orderId.toLowerCase()}`;
}

export function orderCookieName(orderId: string): string {
  return `sc-order-${orderId.toLowerCase()}`;
}

// Path-scoped: the token is sent only to this order's page, never to other orders or stores.
export function orderCookieOptions(slug: string, orderId: string, production: boolean) {
  return { httpOnly: true, sameSite: "lax" as const, secure: production, path: orderPath(slug, orderId), maxAge: ORDER_MAX_AGE_SECONDS };
}
