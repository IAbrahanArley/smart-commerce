import type { Metadata } from "next";
import { notFound } from "next/navigation";
import OrderDetail from "@/components/order-detail";
import { validRouteId } from "@/lib/route-ids";

export const metadata: Metadata = { title: "Pedido | Smart Commerce", robots: { index: false, follow: false } };

export default async function OrderPage({ params }: { params: Promise<{ id: string; orderId: string }> }) {
  const { id, orderId } = await params;
  if (!validRouteId(orderId)) notFound();
  return <OrderDetail key={`${id}-${orderId}`} tenantId={id} orderId={orderId} />;
}
