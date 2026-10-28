import { loadSitemap } from "@/lib/public-store";
import { sitemapXml } from "@/lib/seo";
import { siteOrigin } from "@/lib/store-metadata";

export const dynamic = "force-dynamic";

// SC-047 (ADR-0018 V11): one sitemap per store, with only what the published store shows. Built
// from the same 60 s cached read as the pages, so hiding a product drops it on the next build.
export async function GET(request: Request, { params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const content = await loadSitemap(slug);
  if (!content) return new Response("Not found", { status: 404, headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });
  const origin = siteOrigin() ?? new URL(request.url).origin;
  return new Response(sitemapXml(origin, slug, content), {
    headers: { "Content-Type": "application/xml; charset=utf-8", "Cache-Control": "no-store" },
  });
}
