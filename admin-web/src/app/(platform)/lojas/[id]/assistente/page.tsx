import type { Metadata } from "next";
import StoreAdvisor from "@/components/store-advisor";

export const metadata: Metadata = { title: "Assistente | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }> };

// The conversation lives only in this page (ADR-0016 A7): leaving or reloading starts a new one.
export default async function AdvisorPage({ params }: Props) {
  const { id } = await params;
  return <StoreAdvisor key={id} tenantId={id} />;
}
