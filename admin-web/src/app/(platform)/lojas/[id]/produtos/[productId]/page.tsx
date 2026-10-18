import type { Metadata } from "next";
import Catalog from "@/components/catalog";

export const metadata: Metadata = { title: "Editar produto | Smart Commerce", robots: { index: false, follow: false } };

export default async function ProductPage({ params, searchParams }: { params: Promise<{ id: string; productId: string }>; searchParams: Promise<{ criado?: string | string[] }> }) {
  const [{ id, productId }, { criado }] = await Promise.all([params, searchParams]);
  return <Catalog key={`${id}-${productId}`} tenantId={id} productId={productId} view="edit" created={criado === "1"} />;
}
