import type { Metadata } from "next";
import OrderDetail from "@/components/order-detail";

export const metadata: Metadata = { title: "Pedido | Smart Commerce", robots: { index: false, follow: false } };

export default async function OrderPage({ params }: { params: Promise<{ id: string; orderId: string }> }) {
  const { id, orderId } = await params;
  return <OrderDetail key={`${id}-${orderId}`} tenantId={id} orderId={orderId} />;
}
