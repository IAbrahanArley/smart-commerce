"use client";

import Link from "next/link";
import { useEffect, useId, useState } from "react";
import { apiRequest } from "@/lib/auth-api";
import { formatBRL } from "@/lib/catalog";
import { bars, type Dashboard, type DashboardStock, optionsText, shortDay } from "@/lib/dashboard";
import { formatDay } from "@/lib/expenses";
import { PERIODS, type ReportPeriodKey, missingText, percentText } from "@/lib/finance";
import { formatDateTime } from "@/lib/orders";
import { explainHref, type ExplainTopic } from "@/lib/advisor";
import StatusBadge from "@/components/status-badge";
import PageState from "@/components/page-state";

const CHART_WIDTH = 600;
const CHART_HEIGHT = 160;

// SC-022: the store's period at a glance (ADR-0015 F8, F9). The figures are the financial
// report's; the chart is plain SVG with the design tokens and an equivalent table.
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

  const chart = data ? bars(data.salesByDay, CHART_WIDTH, CHART_HEIGHT) : [];
  const step = Math.max(1, Math.ceil(chart.length / 8));
  return <section className="side-card" aria-labelledby="dashboard-title" aria-busy={state === "loading"}>
    <h2 id="dashboard-title">Desempenho</h2>
    <form className="order-filter" method="get" action={storePath}>
      <label htmlFor="dashboard-period">Período</label>
      <select id="dashboard-period" name="periodo" defaultValue={period}>
        {PERIODS.map(item => <option key={item.key} value={item.key}>{item.label}</option>)}
      </select>
      <button type="submit">Ver período</button>
    </form>
    {state === "loading" ? <PageState headingLevel={3} kind="loading" icon="chart" title="Consultando o desempenho…">Carregando vendas e indicadores de estoque.</PageState> : null}
    {state === "signed-out" ? <><p role="status">Sua sessão expirou. Entre novamente para acessar o desempenho.</p><Link href="/entrar">Entrar</Link></> : null}
    {state === "missing" ? <PageState headingLevel={3} icon="store" title="Loja indisponível" href="/lojas" actionLabel="Voltar para minhas lojas">Escolha uma loja à qual você tem acesso.</PageState> : null}
    {state === "error" ? <PageState headingLevel={3} kind="error" icon="chart" title="Não foi possível consultar o desempenho" action={() => { setState("loading"); setAttempt(current => current + 1); }} actionLabel="Consultar novamente">Confira a conexão e tente carregar os indicadores novamente.</PageState> : null}
    {state === "ready" && data ? <>
      <p className="field-help">De {formatDay(data.period.firstDay)} a {formatDay(data.period.lastDay)} (horário de Brasília).</p>
      <div className="stat-grid">
        <Stat label="Pedidos pagos" value={new Intl.NumberFormat("pt-BR").format(data.paidOrders)} explain={explainOf("PAID_ORDERS")} />
        <Stat label="Receita" value={formatBRL(data.revenue)} explain={explainOf("REVENUE")} />
        <Stat label="Ticket médio" value={data.averageTicket ? formatBRL(data.averageTicket) : "—"} explain={explainOf("AVERAGE_TICKET")} />
        <Stat label="Margem estimada" value={`${formatBRL(data.contributionMargin)} (${percentText(data.marginPercent)})`}
          badge={data.marginComplete ? undefined : "Parcial"} detail={data.marginComplete ? undefined : data.missing.map(missingText).join("; ")} explain={explainOf("MARGIN")} />
      </div>
      <figure className="dashboard-chart">
        <figcaption id={`${chartId}-title`}>Receita por dia</figcaption>
        <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT + 24}`} role="img" aria-labelledby={`${chartId}-title ${chartId}-desc`}>
          <desc id={`${chartId}-desc`}>{data.salesByDay.length} {data.salesByDay.length === 1 ? "dia" : "dias"}; os valores estão na tabela logo abaixo.</desc>
          <line x1="0" y1={CHART_HEIGHT} x2={CHART_WIDTH} y2={CHART_HEIGHT} className="dashboard-axis" />
          {chart.map((bar, index) => <g key={bar.day}>
            <rect x={bar.x} y={bar.y} width={bar.width} height={bar.height} className="dashboard-bar"><title>{`${bar.label}: ${formatBRL(bar.revenue)}`}</title></rect>
            {index % step === 0 ? <text x={bar.x + bar.width / 2} y={CHART_HEIGHT + 16} textAnchor="middle" className="dashboard-label">{bar.label}</text> : null}
          </g>)}
        </svg>
      </figure>
      <details>
        <summary>Ver os valores do gráfico em tabela</summary>
        <div className="data-table-wrap"><table className="data-table" role="table">
          <caption>Receita e pedidos pagos por dia</caption>
          <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Dia</th><th role="columnheader" scope="col" className="numeric">Pedidos</th><th role="columnheader" scope="col" className="numeric">Receita</th></tr></thead>
          <tbody role="rowgroup">{data.salesByDay.map(day => <tr role="row" key={day.day}>
            <th role="rowheader" scope="row">{shortDay(day.day)}</th>
            <td role="cell" data-label="Pedidos" className="numeric">{day.orders}</td>
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

function Stat({ label, value, badge, detail, explain }: { label: string; value: string; badge?: string; detail?: string; explain?: string }) {
  return <div className="stat-card"><div>
    <p className="field-help">{label} {badge ? <StatusBadge tone="neutral">{badge}</StatusBadge> : null}</p>
    <p><strong>{value}</strong></p>
    {detail ? <p className="field-help">{detail}</p> : null}
    {explain ? <Link className="explain-link" href={explain} prefetch={false}>Explicar<span className="visually-hidden"> {label.toLowerCase()}</span></Link> : null}
  </div></div>;
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
