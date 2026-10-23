// Only an invalidation signal crosses components; session credentials remain in HttpOnly cookies.
export const SESSION_INVALIDATED = "smart-commerce:session-invalidated";

export function invalidateSession() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_INVALIDATED));
}

export function isProtectedPath(pathname: string) {
  return pathname === "/conta" || pathname === "/lojas" || pathname.startsWith("/lojas/");
}
