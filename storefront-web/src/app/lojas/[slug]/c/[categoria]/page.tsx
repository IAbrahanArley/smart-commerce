import type { Metadata } from "next";
import { StoreFrame } from "@/components/store-frame";
import { CatalogListing } from "@/components/catalog-listing";
import { loadStoreCatalog } from "@/lib/public-store";
import { type SearchParams, apiQuery } from "@/lib/catalog-page";
import { categoryPath } from "@/lib/menu";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ slug: string; categoria: string }>; searchParams: Promise<SearchParams> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { categoria } = await params;
  // From the address, without a second catalog request; the page itself shows the real name.
  const words = categoria.replaceAll("-", " ");
  return { title: words.charAt(0).toUpperCase() + words.slice(1) };
}

// SC-045: a category and its subcategories, with filters, order and pages in the URL.
export default async function CategoryPage({ params, searchParams }: Props) {
  const [{ slug, categoria }, query] = await Promise.all([params, searchParams]);
  const { store, catalog } = await loadStoreCatalog(slug, apiQuery(query, categoria));
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  const path = catalog.category?.path ?? [];
  return <StoreFrame store={store} menu={catalog.menu} currentCategory={path[0]?.slug}>
    <CatalogListing store={store.slug} base={categoryPath(store.slug, categoria)} params={query} catalog={catalog}
      title={catalog.category?.name ?? "Categoria"}
      breadcrumb={[{ name: "Início", href: storeUrl }, ...path.map(item => ({ name: item.name, href: categoryPath(store.slug, item.slug) }))]} />
  </StoreFrame>;
}
