"use client";

import Link from "next/link";
import { useState } from "react";
import StatusBadge from "@/components/status-badge";

export default function DemoStore() {
  const [size, setSize] = useState("M");
  const [cart, setCart] = useState<Record<string, number>>({});
  const quantity = Object.values(cart).reduce((sum, value) => sum + value, 0);
  return <div className="demo-page landing-wrap">
    <div className="demo-banner"><strong>Demonstração da loja</strong><span>Dados fictícios · sem compra ou pagamento</span><Link href="/">Voltar à apresentação</Link></div>
    <div className="demo-store" data-ui>
      <header className="demo-store-header"><strong>Loja Exemplo</strong><a href="#carrinho-demo">Carrinho de demonstração ({quantity})</a></header>
      <div className="demo-store-product">
        <div className="demo-product-placeholder"><p>Camiseta essencial</p><span>Produto ilustrativo<br />Sem foto cadastrada</span></div>
        <section aria-labelledby="demo-store-title"><p>Loja Exemplo / Vestuário</p><h1 id="demo-store-title">Camiseta essencial</h1><p className="demo-price">R$ 79,90</p><p>Algodão, corte reto e cor natural. Um exemplo para explorar as opções do produto.</p><StatusBadge tone="positive">Disponível no exemplo</StatusBadge>
          <fieldset className="demo-sizes"><legend>Tamanho</legend>{["P", "M", "G"].map(value => <label key={value}><input type="radio" name="demo-size" value={value} checked={size === value} onChange={() => setSize(value)} />{value} {size === value ? "✓" : ""}</label>)}</fieldset>
          <p>Retirada na loja ou frete fixo. Opções ilustrativas.</p><button className="landing-button" onClick={() => setCart(previous => ({ ...previous, [size]: (previous[size] ?? 0) + 1 }))}>Adicionar ao carrinho de demonstração</button><p className="landing-note">Apenas nesta página. Nenhum pedido é criado.</p>
        </section>
      </div>
      <section id="carrinho-demo" className="demo-cart" aria-labelledby="cart-title"><h2 id="cart-title">Carrinho de demonstração</h2><p aria-live="polite">{quantity ? `${quantity} unidade(s) de Camiseta essencial no exemplo. ${Object.entries(cart).map(([option, count]) => `Tamanho ${option}: ${count}`).join(". ")}.` : "Seu carrinho de demonstração está vazio."}</p>{quantity > 0 ? <button onClick={() => setCart({})}>Esvaziar carrinho de demonstração</button> : null}<p>Checkout e pagamentos em desenvolvimento. A seleção é local e se perde ao sair desta página.</p></section>
    </div>
  </div>;
}
