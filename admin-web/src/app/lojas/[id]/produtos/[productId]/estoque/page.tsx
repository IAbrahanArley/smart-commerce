import type { Metadata } from "next";
import Inventory from "@/components/inventory";
import { notFound } from "next/navigation";

export const metadata: Metadata = { title: "Estoque | Smart Commerce", robots: { index: false, follow: false } };

export default async function InventoryPage({ params, searchParams }: { params: Promise<{ id: string; productId: string }>; searchParams: Promise<{ variantId?: string | string[] }> }) {
  const [{ id, productId }, { variantId }] = await Promise.all([params, searchParams]);
  if (variantId && (typeof variantId !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(variantId))) notFound();
  return <Inventory key={`${id}-${productId}-${variantId ?? "simple"}`} tenantId={id} productId={productId} variantId={variantId as string | undefined} />;
}
