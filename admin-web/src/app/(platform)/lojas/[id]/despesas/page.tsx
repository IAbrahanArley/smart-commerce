import type { Metadata } from "next";
import StoreExpenses from "@/components/store-expenses";
import { expenseMonth } from "@/lib/expenses";

export const metadata: Metadata = { title: "Despesas | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ mes?: string | string[] }> };

// The month lives in the URL (?mes=AAAA-MM) so it survives reloads; invalid values fall back to now.
export default async function ExpensesPage({ params, searchParams }: Props) {
  const { id } = await params;
  const { mes } = await searchParams;
  const month = expenseMonth(typeof mes === "string" ? mes : undefined, new Date());
  return <StoreExpenses key={`${id}-${month}`} tenantId={id} month={month} />;
}
