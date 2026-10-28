import type { OrderStatus, OrderView } from "@/lib/orders-view";
import { formatBRL } from "@/lib/price";

export const ORDER_STATUS: Record<OrderStatus, { label: string; text: string }> = {
  PENDING_PAYMENT: { label: "Aguardando pagamento", text: "Os itens estão reservados até o horário abaixo. Conclua o pagamento antes desse prazo." },
  PAID: { label: "Pagamento confirmado", text: "O Mercado Pago confirmou o pagamento à loja. Acompanhe a retirada ou a entrega com a loja." },
  REFUNDED: { label: "Pedido reembolsado", text: "A loja reembolsou este pedido pelo Mercado Pago. O prazo para o valor aparecer depende do meio de pagamento usado." },
  EXPIRED: { label: "Reserva expirada", text: "O prazo da reserva terminou e os itens foram liberados. Se ainda quiser comprar, monte o carrinho novamente." },
  CANCELLED: { label: "Pedido cancelado", text: "Este pedido foi cancelado pela loja e os itens foram liberados." },
};

const DEADLINE = new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", dateStyle: "short", timeStyle: "short" });

export function OrderDeadline({ order }: { order: OrderView }) {
  return order.status === "PENDING_PAYMENT"
    ? <p className="order-deadline">Reserva válida até <time dateTime={order.reservationExpiresAt}>{DEADLINE.format(new Date(order.reservationExpiresAt))}</time> (horário de Brasília).</p>
    : null;
}

// SC-008/SC-046: items, delivery and values of an order, shared by the order page (buyer token)
// and "Acompanhar pedido" (number and e-mail). Never shows the buyer's contact or address.
export function OrderSummary({ order, headingLevel = 2, note }: { order: OrderView; headingLevel?: 2 | 3; note?: string }) {
  const Heading = headingLevel === 2 ? "h2" : "h3";
  return <div className="checkout-layout">
    <section className="order-details" aria-label="Itens e entrega">
      <Heading>Itens</Heading>
      <ul className="checkout-items">
        {order.items.map((item, index) => (
          <li key={index}>
            <span className="checkout-item-name">{item.quantity} × {item.productName}</span>
            {item.options.length > 0 ? <span className="cart-line-options">{item.options.map(option => `${option.name}: ${option.value}`).join(" · ")}</span> : null}
            <span className="checkout-item-total">{formatBRL(item.lineTotal)}</span>
          </li>
        ))}
      </ul>
      <Heading>{order.fulfillment.type === "PICKUP" ? "Retirada na loja" : "Entrega com frete fixo"}</Heading>
      <p className="order-fulfillment">{order.fulfillment.text}</p>
    </section>
    <aside className="cart-summary" aria-label="Valores do pedido">
      <Heading>Valores</Heading>
      <dl className="order-values">
        <div><dt>Itens</dt><dd>{formatBRL(order.subtotal)}</dd></div>
        <div><dt>Entrega</dt><dd>{order.shipping === "0.00" ? "Sem custo" : formatBRL(order.shipping)}</dd></div>
        <div className="order-total"><dt>Total</dt><dd>{formatBRL(order.total)}</dd></div>
      </dl>
      {note ? <p className="cart-note">{note}</p> : null}
    </aside>
  </div>;
}
