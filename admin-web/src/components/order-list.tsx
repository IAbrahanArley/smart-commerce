"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { ORDER_STATUSES, type OrderPage, type OrderStatus, STATUS_LABELS, formatDateTime, ordersApiPath, ordersPagePath } from "@/lib/orders";
import PageHeader from "@/components/page-header";
import StatusBadge from "@/components/status-badge";
import PageState from "@/components/page-state";

const SHORT_FULFILLMENT = { PICKUP: "Retirada", FIXED_SHIPPING: "Frete fixo" } as const;

// Filter and page live in the URL (?situacao=&apos=), so they survive reloads and can be shared.
export default function OrderList({ tenantId, status, after }: { tenantId: string; status: OrderStatus | null; after: string | null }) {
  const [page, setPage] = useState<OrderPage | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(ordersApiPath(tenantId, status, after), { signal: controller.signal }).then(async response => {
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: OrderPage = await response.json();
      if (controller.signal.aborted) return;
      setPage(current); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, status, after, attempt]);

  const filtered = status ? `com situação “${STATUS_LABELS[status]}”` : "";
  return <section className="page" aria-labelledby="orders-title">
    <PageHeader id="orders-title" title="Pedidos" description="Pedidos feitos na vitrine. Pedidos aguardando pagamento reservam o estoque por 30 minutos." />
    <form className="order-filter" method="get" action={`${storePath}/pedidos`}>
      <label htmlFor="order-status">Situação</label>
      <select id="order-status" name="situacao" defaultValue={status ?? ""}>
        <option value="">Todas</option>
        {ORDER_STATUSES.map(value => <option key={value} value={value}>{STATUS_LABELS[value]}</option>)}
      </select>
      <button type="submit">Filtrar pedidos</button>
    </form>
    {state === "loading" ? <PageState kind="loading" icon="receipt" title="Consultando pedidos…">Carregando os pedidos da sua loja.</PageState> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar os pedidos.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState kind="error" icon="receipt" title="Não foi possível consultar os pedidos" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar pedidos novamente">Confira a conexão e tente carregar a lista novamente.</PageState> : null}
    {state === "ready" && page ? page.orders.length ? <>
      <div className="data-table-wrap"><table className="data-table data-table-wrapping" role="table">
        <caption>{after ? "Pedidos mais antigos" : "Pedidos mais recentes"} {filtered}</caption>
        <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Pedido</th><th role="columnheader" scope="col">Situação</th><th role="columnheader" scope="col">Comprador</th><th role="columnheader" scope="col">Entrega</th><th role="columnheader" scope="col" className="numeric">Total</th></tr></thead>
        <tbody role="rowgroup">{page.orders.map(order => <tr role="row" key={order.id}>
          <th role="rowheader" scope="row"><Link href={`${storePath}/pedidos/${encodeURIComponent(order.id)}`}>Pedido nº {order.number}</Link><span className="cell-hint order-created">Criado em {formatDateTime(order.createdAt)}</span></th>
          <td role="cell" data-label="Situação"><StatusBadge tone={order.status === "PENDING_PAYMENT" || order.status === "PAID" ? "positive" : "neutral"}>{STATUS_LABELS[order.status]}</StatusBadge></td>
          <td role="cell" data-label="Comprador">{order.buyerName ?? "Dados removidos"}</td>
          <td role="cell" data-label="Entrega">{SHORT_FULFILLMENT[order.fulfillmentType]} · {order.units === 1 ? "1 unidade" : `${new Intl.NumberFormat("pt-BR").format(order.units)} unidades`}</td>
          <td role="cell" data-label="Total" className="numeric">{formatBRL(order.total)}</td>
        </tr>)}</tbody>
      </table></div>
      <nav className="pagination" aria-label="Páginas de pedidos">
        {after ? <Link href={ordersPagePath(tenantId, status, null)}>Voltar aos mais recentes</Link> : null}
        {page.nextCursor ? <Link href={ordersPagePath(tenantId, status, page.nextCursor)}>Ver pedidos mais antigos</Link> : null}
      </nav>
    </> : <PageState icon="receipt" title={status || after ? "Nenhum pedido encontrado" : "Sua loja ainda não tem pedidos"}>{status || after ? "Tente outra situação para consultar os pedidos." : "Os pedidos feitos na vitrine aparecerão aqui, com os detalhes de pagamento e entrega."}</PageState> : null}
  </section>;
}
