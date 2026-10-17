import type { Metadata } from "next";
import Link from "next/link";
import { StoreHeader } from "@/components/store-header";
import { loadProduct } from "@/lib/public-store";
import { Arrow } from "@/components/arrow";
import ProductGallery from "@/components/product-gallery";
import ProductOffer from "@/components/product-offer";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Detalhes do produto" };

export default async function ProductPage({ params }: { params: Promise<{ slug: string; productId: string }> }) {
  const { slug, productId } = await params;
  const { store, product, images } = await loadProduct(slug, productId);
  return (
    <>
      <StoreHeader store={store} />
      <main id="conteudo" className="page-width product-detail" tabIndex={-1}>
        <nav className="breadcrumb" aria-label="Caminho de navegação">
          <Link href={`/lojas/${encodeURIComponent(store.slug)}`} prefetch={false}><Arrow back /> Voltar aos produtos</Link>
        </nav>
        <article className="detail-layout" aria-labelledby="product-title">
          <div className="detail-description">
            <h1 id="product-title">{product.name}</h1>
            <p className="detail-store">Um produto de <span translate="no">{store.name}</span>.</p>
            <ProductGallery slug={store.slug} productId={product.id} images={images} />
            {product.descriptionHtml ? <section className="product-rich-description" aria-labelledby="description-title"><h2 id="description-title">Descrição do produto</h2><div dangerouslySetInnerHTML={{ __html: product.descriptionHtml }} /></section> : null}
            <Link className="text-link" href={`/lojas/${encodeURIComponent(store.slug)}#produtos`} prefetch={false}>Conhecer o catálogo da loja <Arrow /></Link>
          </div>
          <div className="detail-purchase-info">
            <ProductOffer slug={store.slug} product={product} />
            <div className="consultation-note">
              <h2>Gostou deste produto?</h2>
              <p>Adicione ao carrinho para conferir o total calculado pela loja. A finalização da compra ainda não está disponível.</p>
            </div>
          </div>
        </article>
      </main>
    </>
  );
}
