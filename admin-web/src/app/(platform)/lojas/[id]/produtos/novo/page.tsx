import type { Metadata } from "next";
import Catalog from "@/components/catalog";

export const metadata: Metadata = { title: "Novo produto | Smart Commerce", robots: { index: false, follow: false } };

export default async function NewProductPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Catalog key={`${id}-novo`} tenantId={id} view="new" />;
}
