import type { Metadata } from "next";
import StoreCategories from "@/components/store-categories";

export const metadata: Metadata = { title: "Categorias | Smart Commerce", robots: { index: false, follow: false } };

type Props = { params: Promise<{ id: string }> };

export default async function CategoriesPage({ params }: Props) {
  const { id } = await params;
  return <StoreCategories key={id} tenantId={id} />;
}
