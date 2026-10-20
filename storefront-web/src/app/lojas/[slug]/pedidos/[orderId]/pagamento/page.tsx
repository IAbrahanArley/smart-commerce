import type { Metadata } from "next";
import Link from "next/link";
import { cookies } from "next/headers";
import { notFound } from "next/navigation";
import { StoreHeader } from "@/components/store-header";
import { OrderSteps } from "@/components/order-steps";
import { PaymentForm } from "@/components/payment-form";
import { loadCatalog } from "@/lib/public-store";
import { orderCookieName, orderPath, validOrderId, validOrderToken } from "@/lib/order-cookie";
import { loadOrder } from "@/lib/orders";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Pagamento em confirmação", robots: { index: false, follow: false } };

type Props = { params: Promise<{ slug: string; orderId: string }> };

// Checkout Pro return page (SC-013) for every outcome. The query string the provider appends
// (status, payment id) is deliberately never read: only the backend confirms a payment, through the
// provider (SC-014/SC-015). The page shows the order as the backend sees it.
export default async function PaymentReturnPage({ params }: Props) {
  const { slug, orderId } = await params;
  const store = await loadCatalog(slug);
  if (!validOrderId(orderId)) notFound();
  const token = (await cookies()).get(orderCookieName(orderId))?.value;
  if (!validOrderToken(token)) notFound();
  const order = await loadOrder(store.slug, orderId, token);
  const statusHref = orderPath(encodeURIComponent(store.slug), order.id);
  return (
    <>
      <StoreHeader store={store} />
      <main id="conteudo" className="page-width order-page" tabIndex={-1}>
        <h1>Pedido nº {order.number}</h1>
        <OrderSteps current={2} />
        <section className="order-status" aria-labelledby="payment-return-title">
          {order.status === "PENDING_PAYMENT" ? (
            <>
              <h2 id="payment-return-title">Pagamento em confirmação</h2>
              <p>Se você concluiu o pagamento no Mercado Pago, a confirmação chega à loja diretamente pelo Mercado Pago e pode levar alguns minutos. Pix pode demorar um pouco mais.</p>
              <p>Se você desistiu ou o pagamento não foi aprovado, ainda dá para pagar enquanto a reserva estiver válida.</p>
              {order.payable ? <PaymentForm slug={store.slug} orderId={order.id} label="Tentar pagar novamente" /> : null}
            </>
          ) : (
            <>
              <h2 id="payment-return-title">Este pedido não está mais aguardando pagamento</h2>
              <p>Veja a situação atual no acompanhamento do pedido.</p>
            </>
          )}
          <p><Link href={statusHref} prefetch={false}>Ver acompanhamento do pedido</Link></p>
        </section>
      </main>
    </>
  );
}
