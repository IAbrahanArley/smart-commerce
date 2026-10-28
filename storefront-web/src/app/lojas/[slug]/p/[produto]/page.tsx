import type { Metadata } from "next";
import { permanentRedirect } from "next/navigation";
import { loadProductBySlug } from "@/lib/public-store";
import { productMetadata } from "@/lib/product-seo";
import { ProductView } from "@/components/product-view";

// SC-047: no "force-dynamic": it would force no-store on the backend reads, which are cached for
// 60 s per store. The page still renders per request (it reads the cart cookie).

type Props = { params: Promise<{ slug: string; produto: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug, produto } = await params;
  const found = await loadProductBySlug(slug, produto);
  return "movedTo" in found ? { title: "Produto", robots: { index: false, follow: true } } : productMetadata(found);
}

// SC-045: a product by its storefront address. An address the owner changed in the last 90 days
// answers 308 in the proxy (SC-047); this redirect only covers a change made in between.
export default async function ProductBySlugPage({ params }: Props) {
  const { slug, produto } = await params;
  const found = await loadProductBySlug(slug, produto);
  if ("movedTo" in found) permanentRedirect(`/lojas/${encodeURIComponent(slug)}/p/${encodeURIComponent(found.movedTo)}`);
  return <ProductView detail={found} />;
}
