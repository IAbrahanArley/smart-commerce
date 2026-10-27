"use client";

import { useState } from "react";
import type { PublicProduct, PublicVariant } from "@/lib/public-store";
import { formatBRL } from "@/lib/price";
import { Availability } from "@/components/availability";
import { SubmitButton } from "@/components/submit-button";
import { addToCart } from "@/lib/cart-actions";

type Attribute = { name: string; values: string[] };

// Attribute choice needs every variant to share the same ordered option names; otherwise the combination list stays.
function attributesOf(variants: PublicVariant[]): Attribute[] | null {
  const names = variants[0]?.options.map(option => option.name) ?? [];
  if (!names.length || new Set(names).size !== names.length
    || variants.some(variant => variant.options.length !== names.length || variant.options.some((option, index) => option.name !== names[index]))) return null;
  return names.map((name, index) => ({ name, values: [...new Set(variants.map(variant => variant.options[index].value))] }));
}

function matching(variants: PublicVariant[], values: string[]) {
  return variants.find(variant => variant.options.every((option, index) => option.value === values[index]));
}

const QUANTITIES = Array.from({ length: 10 }, (_, index) => index + 1);

/** Whole percent off, rounded down like the backend (ADR-0018 V3); exact cents, no floating point. */
function percentOff(price: string, compareAt: string): number {
  const now = BigInt(price.replace(".", "")), before = BigInt(compareAt.replace(".", ""));
  return Number(((before - now) * BigInt(100)) / before);
}

// SC-045: compareAt holds the "compare at" price of each variant above its price; installments is
// "em até N× no Mercado Pago" or null. Both optional, so the original product route is unchanged.
export default function ProductOffer({ slug, product, compareAt = {}, installments = null }: {
  slug: string; product: PublicProduct; compareAt?: Record<string, string>; installments?: string | null;
}) {
  const [selected, setSelected] = useState(product.variants[0]?.id ?? "");
  const variant = product.variants.find(item => item.id === selected);
  const attributes = product.kind === "VARIABLE" ? attributesOf(product.variants) : null;
  const current = variant?.options.map(option => option.value) ?? [];

  function choose(index: number, value: string) {
    const wanted = current.map((item, position) => position === index ? value : item);
    // Keep the other choices when that combination exists; otherwise switch to the first combination with this value.
    const next = matching(product.variants, wanted) ?? product.variants.find(item => item.options[index].value === value);
    if (next) setSelected(next.id);
  }

  return <>
    {product.kind === "VARIABLE" && attributes ? <div className="variant-choice" role="group" aria-labelledby="variant-choice-title">
      <p id="variant-choice-title" className="variant-choice-title">Escolha a combinação</p>
      {attributes.map((attribute, index) => <fieldset key={attribute.name} className="option-group">
        <legend>{attribute.name}</legend>
        <div className="option-list">{attribute.values.map(value => {
          const candidate = matching(product.variants, current.map((item, position) => position === index ? value : item));
          return <label key={value} className="option">
            <input className="option-input" type="radio" name={`option-${index}`} value={value} checked={current[index] === value} onChange={() => choose(index, value)} />
            <span className="option-label">{value}{candidate && !candidate.available ? <span className="option-note"> · indisponível</span> : null}</span>
          </label>;
        })}</div>
      </fieldset>)}
    </div> : null}
    {product.kind === "VARIABLE" && !attributes ? <div className="variant-choice"><label htmlFor="public-variant">Escolha a combinação</label><select id="public-variant" name="variant" value={selected} onChange={event => setSelected(event.target.value)}>
      {product.variants.map(item => <option key={item.id} value={item.id}>{item.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</option>)}
    </select></div> : null}
    <div aria-live="polite" aria-atomic="true">
      {product.kind === "VARIABLE" && attributes && variant ? <p className="selected-combination">Combinação escolhida: {variant.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</p> : null}
      <p className="price-label">Preço do produto</p>
      {variant && compareAt[variant.id] ? <p className="detail-compare"><s><span className="visually-hidden">De </span>{formatBRL(compareAt[variant.id])}</s> <span className="detail-discount">−{percentOff(variant.price, compareAt[variant.id])}%<span className="visually-hidden"> de desconto</span></span></p> : null}
      <p className="detail-price">{variant && compareAt[variant.id] ? <span className="visually-hidden">por </span> : null}{formatBRL(variant?.price ?? product.price)}</p>
      {installments ? <p className="detail-installments">{installments}</p> : <p className="price-currency">Valor em reais</p>}<p className="offer-availability"><Availability available={variant?.available ?? product.available} /></p>
    </div>
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
