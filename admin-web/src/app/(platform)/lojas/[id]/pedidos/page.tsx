import type { Metadata } from "next";
import OrderList from "@/components/order-list";
import { orderCursor, orderStatus } from "@/lib/orders";

export const metadata: Metadata = { title: "Pedidos | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ situacao?: string | string[]; apos?: string | string[] }> };

export default async function OrdersPage({ params, searchParams }: Props) {
  const { id } = await params;
  const query = await searchParams;
  const first = (value: string | string[] | undefined) => Array.isArray(value) ? value[0] : value;
  const status = orderStatus(first(query.situacao));
  const after = orderCursor(first(query.apos));
  return <OrderList key={`${id}-${status ?? ""}-${after ?? ""}`} tenantId={id} status={status} after={after} />;
}
