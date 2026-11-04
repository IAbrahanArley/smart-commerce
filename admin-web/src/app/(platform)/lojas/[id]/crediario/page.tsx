import type { Metadata } from "next";
import StoreCredit from "@/components/store-credit";
import { filterFromSlug } from "@/lib/credit";

export const metadata: Metadata = { title: "Crediário | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ situacao?: string | string[]; cliente?: string | string[] }> };

// Filter and open customer live in the URL (?situacao=vencidos, ?cliente=<id>) so they survive
// reloads; an invalid filter falls back to every customer, an unknown customer to "not found".
export default async function CreditPage({ params, searchParams }: Props) {
  const { id } = await params;
  const { situacao, cliente } = await searchParams;
  const filter = filterFromSlug(typeof situacao === "string" ? situacao : undefined);
  const customer = typeof cliente === "string" && /^[0-9a-fA-F-]{36}$/.test(cliente) ? cliente : null;
  return <StoreCredit key={`${id}-${filter}-${customer ?? ""}`} tenantId={id} filter={filter} customerId={customer} />;
}
