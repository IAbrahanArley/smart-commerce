import type { Metadata } from "next";
import { permanentRedirect } from "next/navigation";
import { loadProductBySlug } from "@/lib/public-store";
import { ProductView } from "@/components/product-view";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Detalhes do produto" };

// SC-045: a product by its storefront address. An address the owner changed in the last 90 days
// answers 308 to the current one, so shared links keep working.
export default async function ProductBySlugPage({ params }: { params: Promise<{ slug: string; produto: string }> }) {
  const { slug, produto } = await params;
  const found = await loadProductBySlug(slug, produto);
  if ("movedTo" in found) permanentRedirect(`/lojas/${encodeURIComponent(slug)}/p/${encodeURIComponent(found.movedTo)}`);
  return <ProductView detail={found} />;
}
