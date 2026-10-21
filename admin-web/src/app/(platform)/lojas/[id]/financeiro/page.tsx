import type { Metadata } from "next";
import StoreFinance from "@/components/store-finance";
import { reportPeriod } from "@/lib/finance";

export const metadata: Metadata = { title: "Financeiro | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ periodo?: string | string[] }> };

// The period lives in the URL (?periodo=), validated before reaching the API.
export default async function FinancePage({ params, searchParams }: Props) {
  const { id } = await params;
  const { periodo } = await searchParams;
  const period = reportPeriod(typeof periodo === "string" ? periodo : undefined);
  return <StoreFinance key={`${id}-${period}`} tenantId={id} period={period} />;
}
