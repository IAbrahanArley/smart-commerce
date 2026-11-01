"use client";

import Link from "next/link";
import { useEffect, useId, useState, type ReactNode } from "react";
import { Banknote, Receipt, ShoppingBag, TrendingUp } from "lucide-react";
import { apiRequest } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { type Dashboard, type DashboardStock, optionsText, salesPoints, shortDay } from "@/lib/dashboard";
import { formatDay } from "@/lib/expenses";
import { PERIODS, type ReportPeriodKey, missingText, percentText } from "@/lib/finance";
import { formatDateTime } from "@/lib/orders";
import { explainHref, type ExplainTopic } from "@/lib/advisor";
import StatusBadge from "@/components/status-badge";
import PageState from "@/components/page-state";
import { Loading } from "@/components/ui/loading";
import { KpiCard } from "@/components/ui/kpi-card";
import { SalesChart } from "@/components/charts";

// SC-022: the store's period at a glance (ADR-0015 F8, F9). The figures are the financial
// report's; the chart (recharts, design system phase 2) has an equivalent table.
export default function StoreDashboard({ tenantId, period, explain = false }: { tenantId: string; period: ReportPeriodKey; explain?: boolean }) {
  const [data, setData] = useState<Dashboard | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "signed-out" | "missing" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const chartId = useId();
  const storePath = `/lojas/${encodeURIComponent(tenantId)}`;
  const explainOf = (topic: ExplainTopic) => explain ? explainHref(tenantId, topic, period) : undefined;

  useEffect(() => {
    const controller = new AbortController();
    apiRequest(`/api/tenants/${encodeURIComponent(tenantId)}/dashboard?period=${period}`, { signal: controller.signal }).then(async response => {
      if (controller.signal.aborted) return;
      if (response.status === 401) { setState("signed-out"); return; }
      if (response.status === 403 || response.status === 404) { setState("missing"); return; }
      if (!response.ok) throw new Error();
      const current: Dashboard = await response.json();
      if (controller.signal.aborted) return;
      setData(current); setState("ready");
    }).catch(() => { if (!controller.signal.aborted) setState("error"); });
    return () => controller.abort();
  }, [tenantId, period, attempt]);

  return <section className="side-card" aria-labelledby="dashboard-title" aria-busy={state === "loading"}>
    <h2 id="dashboard-title">Desempenho</h2>
    <form className="order-filter" method="get" action={storePath}>
      <label htmlFor="dashboard-period">Período</label>
      <select id="dashboard-period" name="periodo" defaultValue={period}>
        {PERIODS.map(item => <option key={item.key} value={item.key}>{item.label}</option>)}
      </select>
      <button type="submit">Ver período</button>
    </form>
    {state === "loading" ? <Loading context="desempenho da loja" /> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar o desempenho.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState headingLevel={3} icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState headingLevel={3} kind="error" icon="chart" title="Não foi possível consultar o desempenho" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente carregar os indicadores novamente.</PageState> : null}
    {state === "ready" && data ? <>
      <p className="field-help">De {formatDay(data.period.firstDay)} a {formatDay(data.period.lastDay)} (horário de Brasília).</p>
      <div className="stat-grid">
        <Stat icon={<ShoppingBag />} label="Pedidos pagos" value={new Intl.NumberFormat("pt-BR").format(data.paidOrders)} explain={explainOf("PAID_ORDERS")} />
        <Stat icon={<Banknote />} label="Receita" value={formatBRL(data.revenue)} explain={explainOf("REVENUE")} />
        <Stat icon={<Receipt />} label="Ticket médio" value={data.averageTicket ? formatBRL(data.averageTicket) : "—"} explain={explainOf("AVERAGE_TICKET")} />
        <Stat icon={<TrendingUp />} label="Margem estimada" value={`${formatBRL(data.contributionMargin)} (${percentText(data.marginPercent)})`}
          badge={data.marginComplete ? undefined : "Parcial"} detail={data.marginComplete ? undefined : data.missing.map(missingText).join("; ")} explain={explainOf("MARGIN")} />
      </div>
      <figure className="dashboard-chart" aria-labelledby={`${chartId}-title`}>
        <figcaption id={`${chartId}-title`}>Receita (barras) e pedidos pagos (linha) por dia <span className="cell-hint">Os valores estão na tabela logo abaixo.</span></figcaption>
        <SalesChart points={salesPoints(data.salesByDay)} />
      </figure>
      <details>
        <summary>Ver os valores do gráfico em tabela</summary>
        <div className="data-table-wrap"><table className="data-table" role="table">
          <caption>Receita e pedidos pagos por dia</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Dia</th><th role="columnheader" scope="col" className="numeric">Pedidos</th><th role="columnheader" scope="col" className="numeric">Receita</th></tr></thead>
          <tbody role="rowgroup">{data.salesByDay.map(day => <tr role="row" key={day.day}>
            <th role="rowheader" scope="row">{shortDay(day.day)}</th>
            <td role="cell" data-label="Pedidos" className="numeric">{new Intl.NumberFormat("pt-BR").format(day.orders)}</td>
            <td role="cell" data-label="Receita" className="numeric">{formatBRL(day.revenue)}</td>
          </tr>)}</tbody>
        </table></div>
      </details>
      <div className="dashboard-stock-grid"><StockList title={`Estoque baixo (até ${data.lowStockThreshold} unidades disponíveis)`} total={data.lowStockCount} items={data.lowStock} storePath={storePath} explain={explainOf("LOW_STOCK_LIST")}
        empty="Nenhum item com estoque baixo." describe={item => `${item.available} ${item.available === 1 ? "unidade disponível" : "unidades disponíveis"}`} />
      <StockList title={`Sem venda há ${data.staleAfterDays} dias`} total={data.withoutSalesCount} items={data.withoutSales} storePath={storePath} explain={explainOf("WITHOUT_SALES_LIST")}
        empty="Nenhum item com estoque está parado há 30 dias." describe={item => `${item.available} em estoque · ${item.lastSaleAt ? `última venda em ${formatDateTime(item.lastSaleAt)}` : "nunca vendido"}`} /></div>
      <p className="field-help dashboard-footer"><Link href={`${storePath}/financeiro?periodo=${period}`}>Ver o financeiro completo do período</Link></p>
    </> : null}
  </section>;
}

function Stat({ icon, label, value, badge, detail, explain }: { icon: ReactNode; label: string; value: string; badge?: string; detail?: string; explain?: string }) {
  return <KpiCard icon={icon} label={label} value={value} detail={detail} badge={badge ? <StatusBadge tone="neutral">{badge}</StatusBadge> : undefined}>
    {explain ? <Link className="explain-link" href={explain} prefetch={false}>Explicar<span className="visually-hidden"> {label.toLowerCase()}</span></Link> : null}
  </KpiCard>;
}

function StockList({ title, total, items, storePath, empty, describe, explain }: { title: string; total: number; items: DashboardStock[]; storePath: string; empty: string; describe: (item: DashboardStock) => string; explain?: string }) {
  return <div className="dashboard-list">
    <h3>{title}{total > items.length ? ` — ${items.length} de ${total}` : ""}</h3>
    {explain && total > 0 ? <Link className="explain-link" href={explain} prefetch={false}>Explicar<span className="visually-hidden"> {title.toLowerCase()}</span></Link> : null}
    {items.length ? <ul>{items.map(item => <li className="dashboard-stock-item" key={item.variantId}>
      <div><Link href={`${storePath}/produtos/${encodeURIComponent(item.productId)}`}>{item.productName}</Link>
      {item.options.length ? <span className="cell-hint">{optionsText(item.options)}</span> : null}</div>
      <span className="cell-hint">{describe(item)}</span>
    </li>)}</ul> : <p>{empty}</p>}
  </div>;
}
