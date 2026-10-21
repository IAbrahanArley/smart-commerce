import type { Metadata } from "next";
import Link from "next/link";
import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import { StoreHeader } from "@/components/store-header";
import { OrderSteps } from "@/components/order-steps";
import { Arrow } from "@/components/arrow";
import { PaymentForm } from "@/components/payment-form";
import { loadCatalog } from "@/lib/public-store";
import { orderCookieName, validOrderId, validOrderToken } from "@/lib/order-cookie";
import { loadOrder, type OrderStatus } from "@/lib/orders";
import { formatBRL } from "@/lib/price";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Seu pedido", robots: { index: false, follow: false } };

type Props = { params: Promise<{ slug: string; orderId: string }> };

const STATUS: Record<OrderStatus, { label: string; text: string }> = {
  PENDING_PAYMENT: { label: "Aguardando pagamento", text: "Os itens estão reservados até o horário abaixo. Conclua o pagamento antes desse prazo." },
  PAID: { label: "Pagamento confirmado", text: "O Mercado Pago confirmou o pagamento à loja. Acompanhe a retirada ou a entrega com a loja." },
  EXPIRED: { label: "Reserva expirada", text: "O prazo da reserva terminou e os itens foram liberados. Se ainda quiser comprar, monte o carrinho novamente." },
  CANCELLED: { label: "Pedido cancelado", text: "Este pedido foi cancelado pela loja e os itens foram liberados." },
};

const DEADLINE = new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", dateStyle: "short", timeStyle: "short" });

export default async function OrderPage({ params }: Props) {
  const { slug, orderId } = await params;
  const store = await loadCatalog(slug);
  if (!validOrderId(orderId)) notFound();
  // The token cookie is path-scoped to this order; without it the page is the uniform 404.
  const token = (await cookies()).get(orderCookieName(orderId))?.value;
  if (!validOrderToken(token)) notFound();
  const order = await loadOrder(store.slug, orderId, token);
  const status = STATUS[order.status];
  return (
    <>
      <StoreHeader store={store} />
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
          {order.status === "PENDING_PAYMENT" ? (
            <p className="order-deadline">Reserva válida até <time dateTime={order.reservationExpiresAt}>{DEADLINE.format(new Date(order.reservationExpiresAt))}</time> (horário de Brasília).</p>
          ) : null}
          {order.payable ? <PaymentForm slug={store.slug} orderId={order.id} label="Ir para o pagamento" /> : null}
          {order.status === "PENDING_PAYMENT" && !order.payable ? (
            <p>O pagamento online não está disponível nesta loja agora. A loja entrará em contato se necessário.</p>
          ) : null}
        </section>
        <div className="checkout-layout">
          <section className="order-details" aria-labelledby="order-items-title">
            <h2 id="order-items-title">Itens</h2>
            <ul className="checkout-items">
              {order.items.map((item, index) => (
                <li key={index}>
                  <span className="checkout-item-name">{item.quantity} × {item.productName}</span>
                  {item.options.length > 0 ? <span className="cart-line-options">{item.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</span> : null}
                  <span className="checkout-item-total">{formatBRL(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <h2>{order.fulfillment.type === "PICKUP" ? "Retirada na loja" : "Entrega com frete fixo"}</h2>
            <p className="order-fulfillment">{order.fulfillment.text}</p>
          </section>
          <aside className="cart-summary" aria-labelledby="order-total-title">
            <h2 id="order-total-title">Valores</h2>
            <dl className="order-values">
              <div><dt>Itens</dt><dd>{formatBRL(order.subtotal)}</dd></div>
              <div><dt>Entrega</dt><dd>{order.shipping === "0.00" ? "Sem custo" : formatBRL(order.shipping)}</dd></div>
              <div className="order-total"><dt>Total</dt><dd>{formatBRL(order.total)}</dd></div>
            </dl>
            <p className="cart-note">Guarde o número do pedido. Esta página fica disponível somente neste navegador.</p>
          </aside>
        </div>
      </main>
    </>
  );
}
