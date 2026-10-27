import type { Metadata } from "next";
import AccountShell from "@/components/account-shell";
import BillingPanel from "@/components/billing-panel";

export const metadata: Metadata = { title: "Plano e cobrança | Smart Commerce", robots: { index: false, follow: false } };

export default async function BillingPage({ searchParams }: { searchParams: Promise<{ checkout?: string | string[] }> }) {
  const { checkout } = await searchParams;
  return <AccountShell><BillingPanel result={typeof checkout === "string" ? checkout : null} /></AccountShell>;
}
