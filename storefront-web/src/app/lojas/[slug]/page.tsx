import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { StoreHeader } from "@/components/store-header";
import { loadCatalog } from "@/lib/public-store";
import { formatBRL } from "@/lib/price";
import { Arrow } from "@/components/arrow";
import { Availability } from "@/components/availability";
import { ProductCover } from "@/components/product-gallery";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Produtos da loja" };

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<{ after?: string | string[] }> };

export default async function StorePage({ params, searchParams }: Props) {
  const [{ slug }, { after }] = await Promise.all([params, searchParams]);
  if (Array.isArray(after)) notFound();
  const store = await loadCatalog(slug, after);
  const url = `/lojas/${encodeURIComponent(store.slug)}`;
  return (
    <>
      <StoreHeader store={store} />
      <main id="conteudo" className="page-width catalog-page" tabIndex={-1}>
        <section className="catalog-intro" aria-labelledby="catalog-title">
          <div className="intro-content">
            <h1 id="catalog-title" translate="no">{store.name}</h1>
            <p>Conheça os produtos da loja, confira os preços e encontre os detalhes de cada item.</p>
          </div>
          <a className="intro-link" href="#produtos">Explorar catálogo <Arrow /></a>
        </section>
        <section id="produtos" className="catalog-section" aria-labelledby="products-title">
          <div className="section-heading">
            <h2 id="products-title">Produtos da loja</h2>
            {store.products.length > 0 ? <p>{new Intl.NumberFormat("pt-BR").format(store.products.length)} {store.products.length === 1 ? "produto nesta página" : "produtos nesta página"}</p> : null}
          </div>
          {store.products.length === 0 ? (
            <div className="empty-state">
              <h3>{after ? "Fim da lista" : "Nenhum produto publicado"}</h3>
              <p>{after ? "Não há mais produtos nesta página." : "Os produtos da loja aparecerão aqui quando forem publicados."}</p>
            </div>
          ) : (
            <ul className="product-list">
              {store.products.map(product => (
                <li key={product.id}>
                  <article className="product-card">
                    <Link className="product-link" href={`${url}/produtos/${product.id}`} prefetch={false}>
                      <div className="product-description">
                        <ProductCover slug={store.slug} productId={product.id} name={product.name} />
                        <h3>{product.name}</h3>
                        <Availability available={product.available} />
                      </div>
                      <div className="product-summary">
                        <p className="price">{product.kind === "VARIABLE" ? <span className="price-from">A partir de </span> : null}{formatBRL(product.price)}</p>
                        <span className="product-action">Ver detalhes <Arrow /></span>
                      </div>
                    </Link>
                  </article>
                </li>
              ))}
            </ul>
          )}
          <nav className="pagination" aria-label="Páginas de produtos">
            {after ? <Link href={url} prefetch={false}>Voltar ao início da lista</Link> : null}
            {store.nextCursor ? <Link className="action-link" href={`${url}?after=${store.nextCursor}#produtos`} prefetch={false}>Ver mais produtos</Link> : null}
          </nav>
        </section>
      </main>
    </>
  );
}
