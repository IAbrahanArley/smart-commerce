import type { Metadata } from "next";
import Catalog from "@/components/catalog";
import { filtersFromQuery } from "@/lib/product-filters";

export const metadata: Metadata = { title: "Produtos | Smart Commerce", robots: { index: false, follow: false } };

type Query = Record<"busca" | "categoria" | "vitrine" | "ordem", string | string[] | undefined>;

export default async function ProductsPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<Partial<Query>> }) {
  const { id } = await params;
  const query = await searchParams;
  const first = (value: string | string[] | undefined) => Array.isArray(value) ? value[0] : value;
  const filters = filtersFromQuery({ busca: first(query.busca), categoria: first(query.categoria), vitrine: first(query.vitrine), ordem: first(query.ordem) });
  return <Catalog key={id} tenantId={id} view="list" initialFilters={filters} />;
}
