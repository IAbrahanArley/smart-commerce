import type { Metadata } from "next";
import { loadProduct } from "@/lib/public-store";
import { productMetadata } from "@/lib/product-seo";
import { ProductView } from "@/components/product-view";

// SC-047: no "force-dynamic": it would force no-store on the backend reads, which are cached for
// 60 s per store. The page still renders per request (it reads the cart cookie).

type Props = { params: Promise<{ slug: string; productId: string }> };

// The canonical address of this page is the product address (/p/{endereço}).
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug, productId } = await params;
  return productMetadata(await loadProduct(slug, productId));
}

// The original product route keeps working: links shared before SC-045 and the add-to-cart action
// post here. New links use the product address (/p/{endereço}).
export default async function ProductPage({ params }: Props) {
  const { slug, productId } = await params;
  return <ProductView detail={await loadProduct(slug, productId)} />;
}
