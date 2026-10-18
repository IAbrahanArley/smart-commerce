import type { Metadata } from "next";
import StoreFulfillment from "@/components/store-fulfillment";

export const metadata: Metadata = { title: "Entrega | Smart Commerce", robots: { index: false, follow: false } };

export default async function FulfillmentPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StoreFulfillment key={id} tenantId={id} />;
}
