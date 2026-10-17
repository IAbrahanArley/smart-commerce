import type { Metadata } from "next";
import Catalog from "@/components/catalog";

export const metadata: Metadata = { title: "Editar produto | Smart Commerce", robots: { index: false, follow: false } };

export default async function ProductPage({ params }: { params: Promise<{ id: string; productId: string }> }) {
  const { id, productId } = await params;
  return <Catalog key={`${id}-${productId}`} tenantId={id} productId={productId} />;
}
