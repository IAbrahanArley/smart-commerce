"use client";

import { useState } from "react";
import type { PublicProduct } from "@/lib/public-store";
import { formatBRL } from "@/lib/price";
import { Availability } from "@/components/availability";
import { SubmitButton } from "@/components/submit-button";
import { addToCart } from "@/lib/cart-actions";

const QUANTITIES = Array.from({ length: 10 }, (_, index) => index + 1);

export default function ProductOffer({ slug, product }: { slug: string; product: PublicProduct }) {
  const [selected, setSelected] = useState(product.variants[0]?.id ?? "");
  const variant = product.variants.find(item => item.id === selected);
  return <>
    {product.kind === "VARIABLE" ? <div className="variant-choice"><label htmlFor="public-variant">Escolha a combinação</label><select id="public-variant" name="variant" value={selected} onChange={event => setSelected(event.target.value)}>
      {product.variants.map(item => <option key={item.id} value={item.id}>{item.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</option>)}
    </select></div> : null}
    <div aria-live="polite" aria-atomic="true"><p className="price-label">Preço do produto</p><p className="detail-price">{formatBRL(variant?.price ?? product.price)}</p><p className="price-currency">Valor em reais</p><p className="offer-availability"><Availability available={variant?.available ?? product.available} /></p></div>
    {/* Only the selector and quantity are sent; the cart page shows prices quoted by the backend. */}
    <form action={addToCart} className="add-to-cart">
      <input type="hidden" name="slug" value={slug} />
      <input type="hidden" name="variantId" value={variant?.id ?? ""} />
      <div className="quantity-field">
        <label htmlFor="cart-quantity">Quantidade</label>
        <select id="cart-quantity" name="quantity" defaultValue="1">
          {QUANTITIES.map(value => <option key={value} value={value}>{value}</option>)}
        </select>
      </div>
      <SubmitButton pendingLabel="Adicionando…" disabled={!variant?.available}>Adicionar ao carrinho</SubmitButton>
    </form>
  </>;
}
