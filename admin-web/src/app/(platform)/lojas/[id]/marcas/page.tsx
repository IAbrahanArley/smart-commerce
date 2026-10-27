import type { Metadata } from "next";
import StoreBrands from "@/components/store-brands";

export const metadata: Metadata = { title: "Marcas | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }> };

export default async function BrandsPage({ params }: Props) {
  const { id } = await params;
  return <StoreBrands key={id} tenantId={id} />;
}
