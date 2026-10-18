// Untrusted cart cookie (ADR-0012 §2, D5): selectors and quantities only, never prices.
// Kept free of Next/server imports so the smoke script can exercise it directly.

export const CART_MAX_LINES = 20;
export const CART_MAX_QUANTITY = 10;
export const CART_COOKIE_MAX_BYTES = 4096;
const CART_MAX_AGE_SECONDS = 60 * 60 * 24 * 7;

export type CartLine = { variantId: string; quantity: number };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export function validSlug(slug: string): boolean {
  return slug.length >= 3 && slug.length <= 63 && SLUG.test(slug);
}

export function validVariantId(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

export function validQuantity(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 1 && value <= CART_MAX_QUANTITY;
}

// The slug is part of the name and the path scopes the cookie: store A's cart is never read in store B.
export function cartCookieName(slug: string): string {
  return `sc-cart-${slug}`;
}

export function cartCookieOptions(slug: string, production: boolean) {
  return { httpOnly: true, sameSite: "lax" as const, secure: production, path: `/lojas/${slug}`, maxAge: CART_MAX_AGE_SECONDS };
}

// Sanitizes instead of failing: oversized, non-JSON or over-limit carts become empty; invalid lines are dropped.
export function parseCart(raw: string | undefined): CartLine[] {
  if (!raw || raw.length > CART_COOKIE_MAX_BYTES) return [];
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(value) || value.length > CART_MAX_LINES) return [];
  const lines: CartLine[] = [];
  const seen = new Set<string>();
  for (const item of value) {
    if (!item || typeof item !== "object" || Array.isArray(item)) continue;
    const { variantId, quantity } = item as Record<string, unknown>;
    if (!validVariantId(variantId) || !validQuantity(quantity)) continue;
    const id = variantId.toLowerCase();
    if (seen.has(id)) continue;
    seen.add(id);
    lines.push({ variantId: id, quantity });
  }
  return lines;
}

export function serializeCart(lines: CartLine[]): string {
  const value = JSON.stringify(lines.map(({ variantId, quantity }) => ({ variantId, quantity })));
  if (encodeURIComponent(value).length >= CART_COOKIE_MAX_BYTES) throw new Error("Cart cookie too large");
  return value;
}

// Returns null when a new line would exceed the line limit; quantities are capped at the per-line limit.
export function addLine(lines: CartLine[], variantId: string, quantity: number): CartLine[] | null {
  const id = variantId.toLowerCase();
  const existing = lines.find(line => line.variantId === id);
  if (existing) return lines.map(line => line.variantId === id ? { variantId: id, quantity: Math.min(CART_MAX_QUANTITY, line.quantity + quantity) } : line);
  if (lines.length >= CART_MAX_LINES) return null;
  return [...lines, { variantId: id, quantity }];
}

export function setLine(lines: CartLine[], variantId: string, quantity: number): CartLine[] {
  const id = variantId.toLowerCase();
  return lines.map(line => line.variantId === id ? { variantId: id, quantity } : line);
}

export function removeLine(lines: CartLine[], variantId: string): CartLine[] {
  const id = variantId.toLowerCase();
  return lines.filter(line => line.variantId !== id);
}
