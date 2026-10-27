import type { Metadata } from "next";
import { loadProduct } from "@/lib/public-store";
import { ProductView } from "@/components/product-view";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Detalhes do produto" };

// The original product route keeps working: links shared before SC-045 and the add-to-cart action
// post here. New links use the product address (/p/{endereço}).
export default async function ProductPage({ params }: { params: Promise<{ slug: string; productId: string }> }) {
  const { slug, productId } = await params;
  return <ProductView detail={await loadProduct(slug, productId)} />;
}
