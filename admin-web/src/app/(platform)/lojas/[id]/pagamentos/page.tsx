import type { Metadata } from "next";
import StorePayments from "@/components/store-payments";

export const metadata: Metadata = { title: "Pagamentos | Smart Commerce", robots: { index: false, follow: false } };

export default async function PaymentsPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ mercadoPago?: string | string[] }> }) {
  const { id } = await params;
  const { mercadoPago } = await searchParams;
  return <StorePayments key={id} tenantId={id} result={typeof mercadoPago === "string" ? mercadoPago : null} />;
}
