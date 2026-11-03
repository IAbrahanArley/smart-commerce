import type { Metadata } from "next";
import StoreCounter from "@/components/store-counter";

export const metadata: Metadata = { title: "Vendas na loja | Smart Commerce", robots: { index: false, follow: false } };

export default async function CounterPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreCounter key={id} tenantId={id} />;
}
