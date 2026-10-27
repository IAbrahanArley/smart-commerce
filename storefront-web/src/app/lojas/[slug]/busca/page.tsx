import type { Metadata } from "next";
import { StoreFrame } from "@/components/store-frame";
import { CatalogListing } from "@/components/catalog-listing";
import { loadStoreCatalog } from "@/lib/public-store";
import { type SearchParams, apiQuery } from "@/lib/catalog-page";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Buscar produtos" };

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<SearchParams> };

// SC-045: search results, or every product when the search is empty ("Ver todos").
export default async function SearchPage({ params, searchParams }: Props) {
  const [{ slug }, query] = await Promise.all([params, searchParams]);
  const { store, catalog } = await loadStoreCatalog(slug, apiQuery(query));
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  const title = catalog.search ? `Resultados para “${catalog.search}”` : "Todos os produtos";
  return <StoreFrame store={store} menu={catalog.menu}>
    <CatalogListing store={store.slug} base={`${storeUrl}/busca`} params={query} catalog={catalog} title={title}
      breadcrumb={[{ name: "Início", href: storeUrl }, { name: catalog.search ? "Busca" : "Todos os produtos", href: `${storeUrl}/busca` }]} />
  </StoreFrame>;
}
