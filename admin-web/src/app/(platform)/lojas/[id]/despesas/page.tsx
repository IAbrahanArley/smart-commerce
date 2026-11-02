import type { Metadata } from "next";
import StoreExpenses from "@/components/store-expenses";
import { expenseMonth, situationFromSlug } from "@/lib/expenses";

export const metadata: Metadata = { title: "Contas a pagar | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ mes?: string | string[]; situacao?: string | string[] }> };

// Month and filter live in the URL (?mes=AAAA-MM&situacao=vencidas) so they survive reloads;
// invalid values fall back to the current month and to every situation.
export default async function ExpensesPage({ params, searchParams }: Props) {
  const { id } = await params;
  const { mes, situacao } = await searchParams;
  const month = expenseMonth(typeof mes === "string" ? mes : undefined, new Date());
  const situation = situationFromSlug(typeof situacao === "string" ? situacao : undefined);
  return <StoreExpenses key={`${id}-${month}-${situation ?? ""}`} tenantId={id} month={month} situation={situation} />;
}
