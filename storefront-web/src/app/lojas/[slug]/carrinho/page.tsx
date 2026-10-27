import type { Metadata } from "next";
import Link from "next/link";
import { cookies } from "next/headers";
import { FocusNotice } from "@/components/focus-notice";
import { StoreFrame } from "@/components/store-frame";
import { Arrow } from "@/components/arrow";
import { SubmitButton } from "@/components/submit-button";
import { loadCatalog } from "@/lib/public-store";
import { quoteCart, type QuoteLine } from "@/lib/cart-quote";
import { cartCookieName, parseCart } from "@/lib/cart-cookie";
import { removeCartItem, updateCartItem } from "@/lib/cart-actions";
import { formatBRL } from "@/lib/price";
import { checkoutConfigured } from "@/lib/checkout-input";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Carrinho" };

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<{ aviso?: string | string[] }> };

const NOTICES: Record<string, string> = {
  adicionado: "Produto adicionado ao carrinho.",
  atualizado: "Quantidade atualizada.",
  removido: "Item removido do carrinho.",
  limite: "O carrinho aceita até 20 produtos diferentes. Remova um item para adicionar outro.",
  invalido: "Não foi possível alterar o carrinho. Tente novamente.",
  revisar: "Alguns itens mudaram de disponibilidade. Revise o carrinho antes de finalizar o pedido.",
};
const QUANTITIES = Array.from({ length: 10 }, (_, index) => index + 1);
const STATUS_TEXT = { AVAILABLE: "Disponível", INSUFFICIENT: "Quantidade indisponível. Reduza a quantidade.", UNAVAILABLE: "Indisponível no momento" };

export default async function CartPage({ params, searchParams }: Props) {
  const [{ slug }, { aviso }] = await Promise.all([params, searchParams]);
  // loadCatalog validates the slug and returns the uniform 404 for drafts before the cookie is read.
  const store = await loadCatalog(slug);
  const lines = parseCart((await cookies()).get(cartCookieName(store.slug))?.value);
  const quote = lines.length > 0 ? await quoteCart(store.slug, lines) : null;
  const notice = typeof aviso === "string" ? NOTICES[aviso] : undefined;
  const storeUrl = `/lojas/${encodeURIComponent(store.slug)}`;
  // D6: the checkout link exists only when explicitly enabled and the whole cart can be bought.
  const canCheckout = checkoutConfigured() && quote !== null && quote.purchasable && quote.delivery.length > 0;
  return (
    <StoreFrame store={store}>
      <main id="conteudo" className="page-width cart-page" tabIndex={-1}>
        <nav className="breadcrumb" aria-label="Caminho de navegação">
          <Link href={storeUrl} prefetch={false}><Arrow back /> Continuar comprando</Link>
        </nav>
        <h1>Seu carrinho</h1>
        {notice ? <FocusNotice className="cart-notice">{notice}</FocusNotice> : null}
        {!quote ? (
          <div className="empty-state cart-empty">
            <h2>Seu carrinho está vazio</h2>
            <p>Escolha um produto e use “Adicionar ao carrinho” para conferir o total calculado pela loja.</p>
            <Link className="action-link" href={`${storeUrl}#produtos`} prefetch={false}>Ver produtos</Link>
          </div>
        ) : (
          <div className="cart-layout">
            <section aria-labelledby="cart-items-title">
              <h2 id="cart-items-title" className="cart-section-title">Itens do carrinho</h2>
              <ul className="cart-lines">
                {quote.lines.map(line => <CartItem key={line.variantId} slug={store.slug} line={line} />)}
              </ul>
            </section>
            <aside className="cart-summary" aria-labelledby="cart-summary-title">
              <h2 id="cart-summary-title">Resumo</h2>
              <dl>
                <dt>Subtotal dos itens disponíveis</dt>
                <dd className="cart-subtotal">{formatBRL(quote.subtotal)}</dd>
              </dl>
              {!quote.purchasable ? <p className="cart-warning">Alguns itens estão indisponíveis ou sem a quantidade pedida e não entram no subtotal.</p> : null}
              {canCheckout ? <Link className="action-link cart-checkout" href={`${storeUrl}/checkout`} prefetch={false}>Finalizar pedido</Link> : null}
              <p className="cart-note">{canCheckout
                ? "Valores calculados pela loja nesta consulta, sem reserva de estoque. Na próxima etapa você escolhe a entrega e confirma o pedido."
                : "Valores calculados pela loja nesta consulta, sem reserva de estoque. A finalização da compra não está disponível no momento."}</p>
            </aside>
          </div>
        )}
      </main>
    </StoreFrame>
  );
}

function CartItem({ slug, line }: { slug: string; line: QuoteLine }) {
  const name = line.productName ?? "Item indisponível";
  const quantityId = `quantity-${line.variantId}`;
  return (
    <li className="cart-line">
      <div className="cart-line-info">
        <h3>{line.productId ? <Link href={`/lojas/${encodeURIComponent(slug)}/produtos/${line.productId}`} prefetch={false}>{name}</Link> : name}</h3>
        {line.options.length > 0 ? <p className="cart-line-options">{line.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</p> : null}
        {line.unitPrice ? <p className="cart-line-unit">Preço unitário: {formatBRL(line.unitPrice)}</p> : null}
        <p><span className={line.status === "AVAILABLE" ? "availability available" : "availability"}>{STATUS_TEXT[line.status]}</span></p>
      </div>
      <div className="cart-line-actions">
        {line.status !== "UNAVAILABLE" ? (
          <form action={updateCartItem} className="cart-quantity-form">
            <input type="hidden" name="slug" value={slug} />
            <input type="hidden" name="variantId" value={line.variantId} />
            <div className="quantity-field">
              <label htmlFor={quantityId}>Quantidade</label>
              <select id={quantityId} name="quantity" defaultValue={String(line.quantity)}>
                {QUANTITIES.map(value => <option key={value} value={value}>{value}</option>)}
              </select>
            </div>
            <SubmitButton className="button-secondary" pendingLabel="Atualizando…" aria-label={`Atualizar quantidade de ${name}`}>Atualizar</SubmitButton>
          </form>
        ) : <p className="cart-line-quantity">Quantidade: {line.quantity}</p>}
        {line.lineTotal ? <p className="cart-line-total"><span className="price-label">Total do item</span> {formatBRL(line.lineTotal)}</p> : null}
        <form action={removeCartItem}>
          <input type="hidden" name="slug" value={slug} />
          <input type="hidden" name="variantId" value={line.variantId} />
          <SubmitButton className="button-link" pendingLabel="Removendo…" aria-label={`Remover ${name} do carrinho`}>Remover</SubmitButton>
        </form>
      </div>
    </li>
  );
}
