import type { Metadata } from "next";
import StoreCash from "@/components/store-cash";

export const metadata: Metadata = { title: "Caixa | Smart Commerce", robots: { index: false, follow: false } };

export default async function CashPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreCash key={id} tenantId={id} />;
}
