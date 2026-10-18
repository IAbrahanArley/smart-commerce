import type { Metadata } from "next";
import StoreDetail from "@/components/store-detail";

export const metadata: Metadata = { title: "Minha loja | Smart Commerce", robots: { index: false, follow: false } };

export default async function StorePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreDetail key={id} tenantId={id} />;
}
