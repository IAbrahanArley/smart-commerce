import type { Metadata } from "next";
import Link from "next/link";
import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import { StoreFrame } from "@/components/store-frame";
import { OrderSteps } from "@/components/order-steps";
import { Arrow } from "@/components/arrow";
import { FocusNotice } from "@/components/focus-notice";
import { PaymentForm } from "@/components/payment-form";
import { loadCatalog } from "@/lib/public-store";
import { StoreContacts } from "@/components/store-contacts";
import { orderCookieName, validOrderId, validOrderToken } from "@/lib/order-cookie";
import { loadOrder } from "@/lib/orders";
import { ORDER_STATUS, OrderDeadline, OrderSummary } from "@/components/order-summary";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Seu pedido", robots: { index: false, follow: false } };

type Props = { params: Promise<{ slug: string; orderId: string }>; searchParams: Promise<{ pagamento?: string | string[] }> };

export default async function OrderPage({ params, searchParams }: Props) {
  const { slug, orderId } = await params;
  // Set by the checkout action when Checkout Pro could not be opened (SC-017 review). Only explains
  // why the buyer is here; the payment state always comes from the backend.
  const notOpened = (await searchParams).pagamento === "nao-aberto";
  const store = await loadCatalog(slug);
  if (!validOrderId(orderId)) notFound();
  // The token cookie is path-scoped to this order; without it the page is the uniform 404.
  const token = (await cookies()).get(orderCookieName(orderId))?.value;
  if (!validOrderToken(token)) notFound();
  const order = await loadOrder(store.slug, orderId, token);
  const status = ORDER_STATUS[order.status];
  return (
    <StoreFrame store={store}>
      <main id="conteudo" className="page-width order-page" tabIndex={-1}>
        <nav className="breadcrumb" aria-label="Caminho de navegação">
          <Link href={`/lojas/${encodeURIComponent(store.slug)}`} prefetch={false}><Arrow back /> Voltar à loja</Link>
        </nav>
        <h1>Pedido nº {order.number}</h1>
        <OrderSteps current={2} />
        <section className="order-status" aria-labelledby="order-status-title">
          <h2 id="order-status-title">Situação: {status.label}</h2>
          <ol className="order-timeline">
            <li data-state="done">Pedido registrado na loja</li>
            <li data-state={order.status === "PENDING_PAYMENT" ? "current" : order.status === "PAID" ? "done" : "closed"}>{status.label}</li>
          </ol>
          <p>{status.text}</p>
          <OrderDeadline order={order} />
          {notOpened && order.payable ? <FocusNotice className="checkout-alert">Não foi possível abrir o pagamento agora. O pedido está registrado e reservado: tente de novo pelo botão abaixo.</FocusNotice> : null}
          {order.payable ? <PaymentForm slug={store.slug} orderId={order.id} label="Ir para o pagamento" /> : null}
          {order.status === "PENDING_PAYMENT" && !order.payable ? (
            <p>O pagamento online não está disponível nesta loja agora. A loja entrará em contato se necessário.</p>
          ) : null}
        </section>
        <OrderSummary order={order} note="Guarde o número do pedido e o e-mail usado na compra: com eles, você acompanha o pedido em “Acompanhar pedido”, em qualquer aparelho." />
        <StoreContacts store={store} />
      </main>
    </StoreFrame>
  );
}
