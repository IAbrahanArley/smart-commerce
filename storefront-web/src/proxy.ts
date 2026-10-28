import { NextResponse, type NextRequest } from "next/server";
import { publicOrigin } from "@/lib/seo";

// SC-047: a product address the owner changed answers a real 308 to the current one, decided
// before the page streams (inside the page it could only be an HTML refresh). Asks the backend a
// small question per product page; on any doubt (timeout, error) the page handles the request.

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export async function proxy(request: NextRequest) {
  if (request.method !== "GET" && request.method !== "HEAD") return NextResponse.next();
  const match = /^\/lojas\/([^/]+)\/p\/([^/]+)$/.exec(request.nextUrl.pathname);
  const api = publicOrigin(process.env.STOREFRONT_API_ORIGIN ?? "http://127.0.0.1:8080");
  if (!match || !api) return NextResponse.next();
  const [, store, product] = match;
  if (store.length < 3 || store.length > 63 || !SLUG.test(store) || product.length > 80 || !SLUG.test(product)) return NextResponse.next();
  try {
    const response = await fetch(`${api}/api/public/stores/${store}/p/${product}/address`, {
      cache: "no-store", credentials: "omit", redirect: "error", headers: { Accept: "application/json" }, signal: AbortSignal.timeout(2000),
    });
    if (!response.ok) return NextResponse.next();
    const movedTo = ((await response.json()) as { movedTo?: unknown }).movedTo;
    if (typeof movedTo !== "string" || movedTo.length > 80 || !SLUG.test(movedTo) || movedTo === product) return NextResponse.next();
    const target = request.nextUrl.clone();
    target.pathname = `/lojas/${store}/p/${movedTo}`;
    return NextResponse.redirect(target, 308);
  } catch {
    return NextResponse.next();
  }
}

export const config = { matcher: "/lojas/:store/p/:product" };
