import type { Metadata } from "next";
import StoreContent from "@/components/store-content";

export const metadata: Metadata = { title: "Aparência e conteúdo | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }> };

export default async function ContentPage({ params }: Props) {
  const { id } = await params;
  return <StoreContent key={id} tenantId={id} />;
}
