import type { Metadata } from "next";
import { StoreFrame } from "@/components/store-frame";
import { CatalogListing } from "@/components/catalog-listing";
import { loadStoreCatalog } from "@/lib/public-store";
import { type SearchParams, apiQuery } from "@/lib/catalog-page";
import { categoryPath } from "@/lib/menu";
import { JsonLd } from "@/components/json-ld";
import { breadcrumbLd, storePath } from "@/lib/seo";
import { siteOrigin, storeMetadata } from "@/lib/store-metadata";

// SC-047: no "force-dynamic": it would force no-store on the backend reads, which are cached for
// 60 s per store. The page still renders per request (it reads the cart cookie).

type Props = { params: Promise<{ slug: string; categoria: string }>; searchParams: Promise<SearchParams> };

/** Only the plain category (or one of its pages) is indexed; filtered and sorted views point to it. */
function filtered(query: SearchParams): boolean {
  return Object.keys(query).some(key => key !== "pagina");
}

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const [{ slug, categoria }, query] = await Promise.all([params, searchParams]);
  // The same request as the page: fetch memoizes it within this render.
  const { store, catalog } = await loadStoreCatalog(slug, apiQuery(query, categoria));
  const name = catalog.category?.name ?? "Categoria";
  const page = !filtered(query) && catalog.page > 1 ? `?pagina=${catalog.page}` : "";
  return storeMetadata({
    title: `${name}${catalog.page > 1 ? ` (página ${catalog.page})` : ""} | ${store.name}`,
    description: `${name} na loja ${store.name}: ${catalog.total} ${catalog.total === 1 ? "produto" : "produtos"}.`,
    path: `${categoryPath(store.slug, categoria)}${page}`,
    index: !filtered(query),
  });
}

// SC-045: a category and its subcategories, with filters, order and pages in the URL.
export default async function CategoryPage({ params, searchParams }: Props) {
  const [{ slug, categoria }, query] = await Promise.all([params, searchParams]);
  const { store, catalog } = await loadStoreCatalog(slug, apiQuery(query, categoria));
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  const path = catalog.category?.path ?? [];
  const origin = siteOrigin();
  return <StoreFrame store={store} frame={catalog.frame} currentCategory={path[0]?.slug}>
    {origin ? <JsonLd data={breadcrumbLd(origin, [{ name: store.name, path: storePath(store.slug) }, ...path.map(item => ({ name: item.name, path: categoryPath(store.slug, item.slug) }))])} /> : null}
    <CatalogListing store={store.slug} base={categoryPath(store.slug, categoria)} params={query} catalog={catalog}
      title={catalog.category?.name ?? "Categoria"}
      breadcrumb={[{ name: "Início", href: storeUrl }, ...path.map(item => ({ name: item.name, href: categoryPath(store.slug, item.slug) }))]} />
  </StoreFrame>;
}
