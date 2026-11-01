import type { Metadata } from "next";
import StoreAccounts from "@/components/store-accounts";

export const metadata: Metadata = { title: "Contas | Smart Commerce", robots: { index: false, follow: false } };

export default async function AccountsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreAccounts key={id} tenantId={id} />;
}
