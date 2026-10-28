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
  const featured = query.destaque === "1";
  const title = catalog.search ? `Resultados para “${catalog.search}”` : featured ? "Destaques" : "Todos os produtos";
  return <StoreFrame store={store} frame={catalog.frame}>
    <CatalogListing store={store.slug} base={`${storeUrl}/busca`} params={query} catalog={catalog} title={title}
      breadcrumb={[{ name: "Início", href: storeUrl }, { name: catalog.search ? "Busca" : title, href: `${storeUrl}/busca${featured ? "?destaque=1" : ""}` }]} />
  </StoreFrame>;
}
