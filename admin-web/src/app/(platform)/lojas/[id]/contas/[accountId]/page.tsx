import type { Metadata } from "next";
import { notFound } from "next/navigation";
import AccountStatement from "@/components/account-statement";
import { validRouteId } from "@/lib/route-ids";

export const metadata: Metadata = { title: "Extrato da conta | Smart Commerce", robots: { index: false, follow: false } };

export default async function StatementPage({ params }: { params: Promise<{ id: string; accountId: string }> }) {
  const { id, accountId } = await params;
  if (!validRouteId(accountId)) notFound();
  return <AccountStatement key={`${id}-${accountId}`} tenantId={id} accountId={accountId} />;
}
