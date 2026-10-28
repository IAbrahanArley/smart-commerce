import Link from "next/link";
import type { PublicProductDetail } from "@/lib/public-store";
import { checkoutConfigured } from "@/lib/checkout-input";
import { installmentsText } from "@/lib/catalog-page";
import { categoryPath } from "@/lib/menu";
import { StoreFrame } from "@/components/store-frame";
import { ProductCard } from "@/components/product-card";
import ProductGallery from "@/components/product-gallery";
import ProductOffer from "@/components/product-offer";
import { JsonLd } from "@/components/json-ld";
import { productStructuredData } from "@/lib/product-seo";

// SC-045: the product page, by its address (/p/{endereço}) or by the original id route. Breadcrumb
// through published categories, gallery, brand, offer with "compare at" price and installments,
// delivery and payment notes, description and related products.
export function ProductView({ detail }: { detail: PublicProductDetail }) {
  const { store, product, images, extras } = detail;
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  const installments = installmentsText(extras.maxInstallments);
  return (
    <StoreFrame store={store} currentCategory={extras.categoryPath[0]?.slug}>
      <main id="conteudo" className="page-width product-detail" tabIndex={-1}>
        {productStructuredData(detail).map((data, index) => <JsonLd key={index} data={data} />)}
        <nav className="breadcrumbs" aria-label="Você está em">
          <ol>
            <li><Link href={storeUrl} prefetch={false}>Início</Link></li>
            {extras.categoryPath.map(item => <li key={item.slug}><Link href={categoryPath(store.slug, item.slug)} prefetch={false}>{item.name}</Link></li>)}
            <li><span aria-current="page">{product.name}</span></li>
          </ol>
        </nav>
        <article className="detail-layout" aria-labelledby="product-title">
          <div className="detail-media">
            <ProductGallery slug={store.slug} productId={product.id} images={images} />
          </div>
          <div className="detail-summary">
            {extras.brand ? <p className="detail-brand">{extras.brand.name}</p> : null}
            <h1 id="product-title">{product.name}</h1>
            <ProductOffer slug={store.slug} product={product} compareAt={extras.variantCompareAt} installments={installments} />
            <ul className="detail-assurances">
              <li><strong>Entrega:</strong> frete da loja ou retirada, escolhidos ao finalizar o pedido.</li>
              <li><strong>Pagamento:</strong> {checkoutConfigured() ? "Pix, cartão e boleto pelo Mercado Pago." : "a finalização da compra ainda não está disponível."}</li>
            </ul>
          </div>
          <div className="detail-description">
            {product.descriptionHtml ? <section className="product-rich-description" aria-labelledby="description-title"><h2 id="description-title">Descrição</h2><div dangerouslySetInnerHTML={{ __html: product.descriptionHtml }} /></section> : null}
          </div>
        </article>
        {extras.related.length ? <section className="home-section" aria-labelledby="related-title">
          <h2 id="related-title">Você também pode gostar</h2>
          <ul className="catalog-grid">
            {extras.related.map(card => <li key={card.id}><ProductCard store={store.slug} card={card} maxInstallments={extras.maxInstallments} /></li>)}
          </ul>
        </section> : null}
      </main>
    </StoreFrame>
  );
}
