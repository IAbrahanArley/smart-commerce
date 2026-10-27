// Only an invalidation signal crosses components; session credentials remain in HttpOnly cookies.
export const SESSION_INVALIDATED = "smart-commerce:session-invalidated";

export function invalidateSession() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_INVALIDATED));
}

// SC-033: a write refused with 402 means the account's standing changed; the billing notice asks again.
export const BILLING_CHANGED = "smart-commerce:billing-changed";

export function isProtectedPath(pathname: string) {
  return pathname === "/conta" || pathname.startsWith("/conta/") || pathname === "/lojas" || pathname.startsWith("/lojas/");
}
