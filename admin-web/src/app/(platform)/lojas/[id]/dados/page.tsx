import type { Metadata } from "next";
import StoreProfileEditor from "@/components/store-profile";

export const metadata: Metadata = { title: "Dados da loja | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }> };

export default async function StoreProfilePage({ params }: Props) {
  const { id } = await params;
  return <StoreProfileEditor key={id} tenantId={id} />;
}
