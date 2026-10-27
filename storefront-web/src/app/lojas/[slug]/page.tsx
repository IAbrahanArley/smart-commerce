import type { Metadata } from "next";
import Link from "next/link";
import { StoreFrame } from "@/components/store-frame";
import { ProductCard } from "@/components/product-card";
import { loadStoreCatalog } from "@/lib/public-store";
import { categoryPath } from "@/lib/menu";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Produtos da loja" };

type Props = { params: Promise<{ slug: string }> };

const NEW_ARRIVALS = 8;

// SC-045: the store home. Category shortcuts and the most recent products; banners and the
// sections the owner chooses come with SC-043 ("Aparência e conteúdo").
export default async function StorePage({ params }: Props) {
  const { slug } = await params;
  const { store, catalog } = await loadStoreCatalog(slug, new URLSearchParams({ ordem: "recentes" }));
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  const products = catalog.products.slice(0, NEW_ARRIVALS);
  return (
    <StoreFrame store={store} menu={catalog.menu}>
      <main id="conteudo" className="page-width store-home" tabIndex={-1}>
        <h1 className="visually-hidden" translate="no">{store.name}</h1>
        {catalog.menu.length ? <section className="home-section" aria-labelledby="home-categories">
          <h2 id="home-categories">Compre por categoria</h2>
          <ul className="category-shortcuts">
            {catalog.menu.map(item => <li key={item.slug}><Link href={categoryPath(store.slug, item.slug)} prefetch={false}>{item.name}</Link></li>)}
          </ul>
        </section> : null}
        <section className="home-section" aria-labelledby="home-new">
          <div className="home-section-heading">
            <h2 id="home-new">Lançamentos</h2>
            {catalog.total > 0 ? <Link href={`${storeUrl}/busca`} prefetch={false}>Ver todos os produtos</Link> : null}
          </div>
          {products.length === 0 ? <div className="empty-state">
            <h3>Nenhum produto publicado</h3>
            <p>Os produtos da loja aparecerão aqui quando forem publicados.</p>
          </div> : <ul className="catalog-grid">
            {products.map(card => <li key={card.id}><ProductCard store={store.slug} card={card} maxInstallments={catalog.maxInstallments} /></li>)}
          </ul>}
        </section>
      </main>
    </StoreFrame>
  );
}
