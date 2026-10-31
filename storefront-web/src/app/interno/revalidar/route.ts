import { revalidateTag } from "next/cache";
import { verifyRevalidation } from "@/lib/revalidation";
import { storeTag } from "@/lib/seo";

export const dynamic = "force-dynamic";

// SC-047: called by the backend after a store change commits. The next visit reads fresh data
// (expire 0), so a hidden product never shows again after this call.
export async function POST(request: Request) {
  // SC-048 review: nothing is read from an unsigned or oversized request.
  const secret = process.env.STOREFRONT_REVALIDATION_SECRET;
  if (!secret || secret.length < 32) return new Response(null, { status: 404, headers: { "Cache-Control": "no-store" } });
  if (!request.headers.get("x-sc-revalidate-signature") || !request.headers.get("x-sc-revalidate-timestamp"))
    return new Response(null, { status: 401, headers: { "Cache-Control": "no-store" } });
  if (Number(request.headers.get("content-length") ?? "0") > 1000) return new Response(null, { status: 413, headers: { "Cache-Control": "no-store" } });
  const body = (await request.text()).slice(0, 1000);
  const check = verifyRevalidation(process.env.STOREFRONT_REVALIDATION_SECRET, body,
    request.headers.get("x-sc-revalidate-timestamp"), request.headers.get("x-sc-revalidate-signature"), Date.now());
  if (!check.ok) return new Response(null, { status: check.status, headers: { "Cache-Control": "no-store" } });
  revalidateTag(storeTag(check.store), { expire: 0 });
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}
