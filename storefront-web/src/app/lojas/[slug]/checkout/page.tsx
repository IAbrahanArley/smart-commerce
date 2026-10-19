import type { Metadata } from "next";
import Link from "next/link";
import { randomUUID } from "node:crypto";
import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { StoreHeader } from "@/components/store-header";
import { Arrow } from "@/components/arrow";
import { CheckoutForm, type CheckoutOption } from "@/components/checkout-form";
import { loadCatalog } from "@/lib/public-store";
import { quoteCart } from "@/lib/cart-quote";
import { cartCookieName, parseCart } from "@/lib/cart-cookie";
import { checkoutConfigured } from "@/lib/checkout-input";
import { addMoney, formatBRL } from "@/lib/price";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Finalizar pedido", robots: { index: false, follow: false } };

type Props = { params: Promise<{ slug: string }> };

export default async function CheckoutPage({ params }: Props) {
  // D6: without explicit configuration the route does not exist.
  if (!checkoutConfigured()) notFound();
  const { slug } = await params;
  const store = await loadCatalog(slug);
  const cartUrl = `/lojas/${encodeURIComponent(store.slug)}/carrinho`;
  const lines = parseCart((await cookies()).get(cartCookieName(store.slug))?.value);
  if (lines.length === 0) redirect(cartUrl);
  const quote = await quoteCart(store.slug, lines);
  if (!quote.purchasable) redirect(`${cartUrl}?aviso=revisar`);
  const options: CheckoutOption[] = quote.delivery.flatMap(option => {
    const total = addMoney(quote.subtotal, option.fee);
    return total ? [{ type: option.type, text: option.text, fee: option.fee, total }] : [];
  });
  return (
    <>
      <StoreHeader store={store} />
      <main id="conteudo" className="page-width checkout-page" tabIndex={-1}>
        <nav className="breadcrumb" aria-label="Caminho de navegação">
          <Link href={cartUrl} prefetch={false}><Arrow back /> Voltar ao carrinho</Link>
        </nav>
        <h1>Finalizar pedido</h1>
        <div className="checkout-layout">
          {options.length > 0 ? (
            // A new key per page load: retries inside this page reuse it; a fresh visit starts a new attempt.
            <CheckoutForm slug={store.slug} storeName={store.name} requestId={randomUUID()} options={options} />
          ) : (
            <div className="empty-state">
              <h2>A loja não está recebendo pedidos agora</h2>
              <p>Nenhuma forma de entrega está disponível no momento. Tente novamente mais tarde.</p>
            </div>
          )}
          <aside className="cart-summary" aria-labelledby="checkout-summary-title">
            <h2 id="checkout-summary-title">Resumo do pedido</h2>
            <ul className="checkout-items">
              {quote.lines.map(line => (
                <li key={line.variantId}>
                  <span className="checkout-item-name">{line.quantity} × {line.productName}</span>
                  {line.options.length > 0 ? <span className="cart-line-options">{line.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</span> : null}
                  <span className="checkout-item-total">{line.lineTotal ? formatBRL(line.lineTotal) : null}</span>
                </li>
              ))}
            </ul>
            <dl>
              <dt>Subtotal dos itens</dt>
              <dd className="cart-subtotal">{formatBRL(quote.subtotal)}</dd>
            </dl>
            <p className="cart-note">O total final depende da entrega escolhida e é confirmado pela loja ao registrar o pedido. Ao confirmar, os itens ficam reservados por 30 minutos.</p>
          </aside>
        </div>
      </main>
    </>
  );
}
