import Link from "next/link";
import { type Card, installmentsText, productPath } from "@/lib/catalog-page";
import { formatBRL } from "@/lib/price";
import { ProductCover } from "@/components/product-gallery";

// SC-045 (DESIGN.md "Vitrine padrão V1"): photo with the discount or "Esgotado" badge, brand, name,
// "compare at" price struck through, price and installments. The whole card is one link.
export function ProductCard({ store, card, maxInstallments }: { store: string; card: Card; maxInstallments: number | null }) {
  const installments = installmentsText(maxInstallments);
  return <article className="catalog-card">
    <Link className="catalog-card-link" href={productPath(store, card)} prefetch={false}>
      <div className="catalog-card-media">
        <ProductCover slug={store} productId={card.id} name={card.name} />
        {!card.available ? <span className="card-badge card-badge-out">Esgotado</span>
          : card.discountPercent ? <span className="card-badge card-badge-discount">−{card.discountPercent}%<span className="visually-hidden"> de desconto</span></span> : null}
      </div>
      {card.brand ? <span className="card-brand">{card.brand}</span> : null}
      <h3 className="card-name">{card.name}</h3>
      <p className="card-prices">
        {card.compareAtPrice ? <><s className="card-compare"><span className="visually-hidden">De </span>{formatBRL(card.compareAtPrice)}</s>{" "}</> : null}
        <strong className="card-price">{card.compareAtPrice ? <span className="visually-hidden">por </span> : null}{formatBRL(card.price)}</strong>
      </p>
      {installments && card.available ? <p className="card-installments">{installments}</p> : null}
    </Link>
  </article>;
}
