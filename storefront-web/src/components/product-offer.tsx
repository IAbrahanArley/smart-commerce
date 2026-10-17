"use client";

import { useState } from "react";
import type { PublicProduct } from "@/lib/public-store";
import { formatBRL } from "@/lib/price";
import { Availability } from "@/components/availability";

export default function ProductOffer({ product }: { product: PublicProduct }) {
  const [selected, setSelected] = useState(product.variants[0]?.id ?? "");
  const variant = product.variants.find(item => item.id === selected);
  return <>
    {product.kind === "VARIABLE" ? <div className="variant-choice"><label htmlFor="public-variant">Escolha a combinação</label><select id="public-variant" name="variant" value={selected} onChange={event => setSelected(event.target.value)}>
      {product.variants.map(item => <option key={item.id} value={item.id}>{item.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</option>)}
    </select></div> : null}
    <div aria-live="polite" aria-atomic="true"><p className="price-label">Preço do produto</p><p className="detail-price">{formatBRL(variant?.price ?? product.price)}</p><p className="price-currency">Valor em reais</p><p className="offer-availability"><Availability available={variant?.available ?? product.available} /></p></div>
  </>;
}
