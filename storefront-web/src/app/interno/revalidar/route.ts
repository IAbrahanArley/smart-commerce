import { revalidateTag } from "next/cache";
import { verifyRevalidation } from "@/lib/revalidation";
import { storeTag } from "@/lib/seo";

export const dynamic = "force-dynamic";

// SC-047: called by the backend after a store change commits. The next visit reads fresh data
// (expire 0), so a hidden product never shows again after this call.
export async function POST(request: Request) {
  const body = (await request.text()).slice(0, 1000);
  const check = verifyRevalidation(process.env.STOREFRONT_REVALIDATION_SECRET, body,
    request.headers.get("x-sc-revalidate-timestamp"), request.headers.get("x-sc-revalidate-signature"), Date.now());
  if (!check.ok) return new Response(null, { status: check.status, headers: { "Cache-Control": "no-store" } });
  revalidateTag(storeTag(check.store), { expire: 0 });
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}
