import type { MetadataRoute } from "next";
import { indexable } from "@/lib/seo";

// SC-047: store pages may be crawled; cart, checkout, orders, tracking and internal routes may not.
// The hosted test environment is closed to crawlers. Each store lists its own sitemap at
// /lojas/{loja}/sitemap.xml (submitted per store; there is no index of all stores).
export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  if (!indexable(process.env.DEPLOYMENT_ENVIRONMENT)) return { rules: { userAgent: "*", disallow: "/" } };
  return {
    rules: {
      userAgent: "*",
      allow: "/lojas/",
      disallow: ["/lojas/*/carrinho", "/lojas/*/checkout", "/lojas/*/pedidos/", "/lojas/*/acompanhar-pedido", "/lojas/*/busca", "/interno/"],
    },
  };
}
