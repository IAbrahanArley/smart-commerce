import type { Metadata } from "next";
import StoreDetail from "@/components/store-detail";
import { reportPeriod } from "@/lib/finance";

export const metadata: Metadata = { title: "Minha loja | Smart Commerce", robots: { index: false, follow: false } };

// The dashboard period lives in the URL (?periodo=), validated before reaching the API (SC-022).
export default async function StorePage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ periodo?: string | string[] }> }) {
  const { id } = await params;
  const { periodo } = await searchParams;
  return <StoreDetail key={id} tenantId={id} period={reportPeriod(typeof periodo === "string" ? periodo : undefined)} />;
}
